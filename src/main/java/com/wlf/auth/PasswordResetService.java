package com.wlf.auth;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.wlf.common.BusinessException;
import com.wlf.common.ErrorCode;
import com.wlf.entity.User;
import com.wlf.mail.MailService;
import com.wlf.user.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;

/**
 * 密码找回：令牌签发 / 校验 / 一次性消费与作废。
 *
 * <h2>跨模块写：本类直接注入 {@link UserMapper}</h2>
 *
 * <h2>本类不持有的东西</h2>
 *
 * <p><b>不持有 {@code JavaMailSender}</b>，只持有 {@link MailService}。发信是同步还是走队列，
 * 是本类<b>不该知道</b>的事——那正是引入 MQ 时希望「本类一行都不用改」的那条接缝。
 */
@Slf4j
@Service
public class PasswordResetService {

    /**
     * 令牌有效期。
     */
    static final Duration TOKEN_TTL = Duration.ofMinutes(30);

    /**
     * 原始令牌的熵：{@code SecureRandom} 32 字节。
     *
     * <p>256 位随机数使「猜」与「离线爆破哈希」都不成立，这也是它只用 SHA-256 存哈希的底气
     * （§6.6 明写不用 BCrypt：那是给低熵口令抗爆破的，用在这里只会拖慢每次校验）。
     */
    private static final int TOKEN_BYTES = 32;

    private final UserMapper userMapper;
    private final PasswordResetTokenMapper tokenMapper;
    private final PasswordResetRateLimiter rateLimiter;
    private final PasswordEncoder passwordEncoder;
    private final MailService mailService;
    private final TransactionTemplate transactionTemplate;

    /** 重置链接的前端地址前缀。见 {@code jotang.mail.reset-link-base}。 */
    private final String resetLinkBase;

    /**
     * 只用来生成令牌，不用来生成会话或密钥。
     *
     * <p>与 {@code JwtTokenProvider} 是两条独立的链路，刻意不复用：那边是 HMAC 签名密钥体系，
     * 这边是一次性随机数。共用一个 {@code SecureRandom} 没有坏处，但共用「令牌」这个概念
     * 会让两边的生命周期、存储方式、失效规则混在一起——它们的规则一条都不相同。
     */
    private final SecureRandom secureRandom = new SecureRandom();

    public PasswordResetService(UserMapper userMapper,
                                PasswordResetTokenMapper tokenMapper,
                                PasswordResetRateLimiter rateLimiter,
                                PasswordEncoder passwordEncoder,
                                MailService mailService,
                                @Value("${jotang.mail.reset-link-base}") String resetLinkBase,
                                PlatformTransactionManager transactionManager) {
        this.userMapper = userMapper;
        this.tokenMapper = tokenMapper;
        this.rateLimiter = rateLimiter;
        this.passwordEncoder = passwordEncoder;
        this.mailService = mailService;
        this.resetLinkBase = resetLinkBase;
        // 自己 new 而不依赖托管 Bean，与 NoteService 同例：要的是一段明确可控的事务边界
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * 申请重置：限频 → 查用户 → 签发令牌 → 发信。
     *
     * <p><b>当前这一条只做到了「响应相同」，没做到「耗时相近」。</b>发信是同步的
     * （Phase 1），命中分支要等一次 SMTP 往返（几百毫秒），未命中分支立即返回——
     * 这个差异足以用来做时间侧信道枚举。<b>异步化（把发信挪进消息队列）正是为了消掉它</b>：
     * 两条分支都退化成「写库 + 投一条消息」，耗时天然对齐。见 §8.4。
     */
    public void forgot(String email) {
        String trimmed = email.trim();
        // 限频放最前：被拒的请求不该往下走，更不该有机会发出邮件
        rateLimiter.requireSlot(trimmed);

        User user = userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getEmail, trimmed));
        if (user == null) {
            // 静默返回。看起来和成功一模一样，防用户枚举。
            return;
        }

        String rawToken = generateToken();
        String tokenHash = sha256Hex(rawToken);

        // 事务：作废旧令牌 + 写入新令牌。
        transactionTemplate.executeWithoutResult(status -> {
            tokenMapper.invalidateAllUnused(user.getId());
            tokenMapper.insertToken(user.getId(), tokenHash, TOKEN_TTL.toMinutes());
        });

        // 发信在事务提交之后：网络调用不跨事务边界。
        sendQuietly(user.getEmail(), rawToken);
    }

    /**
     * 用令牌设置新密码。
     *
     * <p>成败由一条条件 UPDATE 的影响行数决定，不是先查出来判断。
     * 两个并发请求拿着同一个令牌同时打进来时，只有一条能改到那一行，另一条得到 0
     * 天然抗重放，不需要任何额外的锁。
     */
    public void reset(String token, String newPassword) {
        String tokenHash = sha256Hex(token.trim());

        // 先按哈希查出归属。
        // 挡住“拿随机串打这个接口”白白消耗一次 BCrypt 的可能
        Long userId = tokenMapper.selectUserIdByTokenHash(tokenHash);
        if (userId == null) {
            throw invalidToken();
        }

        // 编码放在事务外：BCrypt 要几十到上百毫秒，圈进事务等于让两行的 X 锁陪着它等
        String encoded = passwordEncoder.encode(newPassword);

        transactionTemplate.executeWithoutResult(status -> {
            if (tokenMapper.consume(tokenHash) == 0) {
                // 令牌不存在 / 已用过 / 已过期，三种情形同一个结果，也报同一个错
                throw invalidToken();
            }
            userMapper.update(null, Wrappers.<User>lambdaUpdate()
                    .eq(User::getId, userId)
                    .set(User::getPasswordHash, encoded));
            // 改密成功即作废该用户其余未消费的令牌。
            tokenMapper.invalidateAllUnused(userId);
        });
    }

    /**
     * 发信；失败只记日志，不影响本次请求。
     *
     * <p>日志不记令牌，只记收件人：令牌只该出现在收件人的信箱里。
     */
    private void sendQuietly(String to, String rawToken) {
        try {
            String link = PasswordResetMailContent.link(resetLinkBase, rawToken);
            mailService.send(to, PasswordResetMailContent.SUBJECT,
                    PasswordResetMailContent.body(link, TOKEN_TTL.toMinutes()));
        } catch (RuntimeException e) {
            log.error("重置邮件发送失败：令牌已入库仍可用，用户需重新申请 email={}", to, e);
        }
    }

    private static BusinessException invalidToken() {
        return new BusinessException(ErrorCode.PARAM_INVALID,
                "重置链接无效、已过期或已被使用（若收到多封邮件，请使用最新的一封）");
    }

    /** 32 字节随机数 → URL-safe Base64（无补位）。字母表不含需要转义的字符，见 PasswordResetMailContent#link */
    private String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** SHA-256 → 64 位小写 hex，恰好填满 {@code token_hash CHAR(64)} */
    private static String sha256Hex(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 是 JVM 必须实现的算法之一，走不到这里
            throw new IllegalStateException("JVM 不支持 SHA-256", e);
        }
    }
}
