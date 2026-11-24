package com.wlf.user.application.service;

import com.wlf.common.BusinessException;
import com.wlf.common.ErrorCode;
import com.wlf.user.application.command.ForgotPasswordCommand;
import com.wlf.user.application.command.ResetPasswordCommand;
import com.wlf.user.application.policy.PasswordResetRateLimiter;
import com.wlf.user.application.template.PasswordResetMailContent;
import com.wlf.user.domain.notification.MailService;
import com.wlf.user.domain.passwordreset.PasswordResetToken;
import com.wlf.user.domain.passwordreset.PasswordResetTokenRepository;
import com.wlf.user.domain.passwordreset.model.TokenHash;
import com.wlf.user.domain.user.PasswordHasher;
import com.wlf.user.domain.user.User;
import com.wlf.user.domain.user.UserRepository;
import com.wlf.user.domain.user.model.Email;
import com.wlf.user.domain.user.model.PasswordHash;
import com.wlf.user.domain.user.model.RawPassword;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

/**
 * 密码找回：令牌签发 / 校验 / 一次性消费与作废。
 *
 * <p>跨模块写：本类通过 {@link UserRepository} 写 user 表。
 */
@Slf4j
@Service
public class PasswordResetService {

    /** 原始令牌的熵：{@code SecureRandom} 32 字节。*/
    private static final int TOKEN_BYTES = 32;

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordResetRateLimiter rateLimiter;
    private final PasswordHasher passwordHasher;
    private final MailService mailService;
    private final TransactionTemplate transactionTemplate;

    /** 重置链接的前端地址前缀。见 {@code jotang.mail.reset-link-base}。 */
    private final String resetLinkBase;

    /**
     * 只用来生成令牌，不用来生成会话或密钥。
     */
    private final SecureRandom secureRandom = new SecureRandom();

    public PasswordResetService(UserRepository userRepository,
                                PasswordResetTokenRepository tokenRepository,
                                PasswordResetRateLimiter rateLimiter,
                                PasswordHasher passwordHasher,
                                MailService mailService,
                                @Value("${jotang.mail.reset-link-base}") String resetLinkBase,
                                PlatformTransactionManager transactionManager) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.rateLimiter = rateLimiter;
        this.passwordHasher = passwordHasher;
        this.mailService = mailService;
        this.resetLinkBase = resetLinkBase;
        // 自己 new 而不依赖托管 Bean，与 NoteService 同例：要的是一段明确可控的事务边界
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * 申请重置：限频 → 查用户 → 签发令牌 → 发信。
     */
    public void forgot(ForgotPasswordCommand command) {
        String trimmed = command.email().trim();
        // 限频放最前：被拒的请求不该往下走，更不该有机会发出邮件
        rateLimiter.requireSlot(trimmed);

        Email emailValue;
        try {
            emailValue = new Email(trimmed);
        } catch (BusinessException e) {
            // 邮箱格式不合法等价于查无此人，保持静默
            return;
        }

        User user = userRepository.findByEmail(emailValue).orElse(null);
        if (user == null) {
            // 静默返回。看起来和成功一模一样，防用户枚举。
            return;
        }

        String rawToken = generateToken();
        TokenHash tokenHash = new TokenHash(sha256Hex(rawToken));
        LocalDateTime issuedAt = currentTime();

        PasswordResetToken newToken = PasswordResetToken.issue(user.id(), tokenHash, issuedAt);

        // 事务：作废旧令牌 + 写入新令牌。
        transactionTemplate.executeWithoutResult(status -> {
            tokenRepository.invalidateAllUnused(user.id(), issuedAt);
            tokenRepository.insert(newToken);
        });

        // 发信在事务提交之后：网络调用不跨事务边界。
        sendQuietly(user.email().value(), rawToken);
    }

    /**
     * 用令牌设置新密码。
     */
    public void reset(ResetPasswordCommand command) {
        TokenHash tokenHash = new TokenHash(sha256Hex(command.token().trim()));

        // 先按哈希查出令牌；不存在、已用过、已过期都按同一个错误处理。
        PasswordResetToken resetToken = tokenRepository.findByTokenHash(tokenHash).orElse(null);
        if (resetToken == null || !resetToken.isUsable(currentTime())) {
            throw invalidToken();
        }

        // 编码放在事务外：BCrypt 要几十到上百毫秒，圈进事务等于让两行的 X 锁陪着它等
        PasswordHash encoded = passwordHasher.hash(new RawPassword(command.newPassword()));

        transactionTemplate.executeWithoutResult(status -> {
            LocalDateTime now = currentTime();
            resetToken.consume(now);
            if (!tokenRepository.consume(tokenHash, now)) {
                // 并发兜底：另一个请求先消费了这枚令牌
                throw invalidToken();
            }
            User user = userRepository.findById(resetToken.userId())
                    .orElseThrow(PasswordResetService::invalidToken);
            user.changePassword(encoded);
            userRepository.updatePassword(user);
            // 改密成功即作废该用户其余未消费的令牌。
            tokenRepository.invalidateAllUnused(resetToken.userId(), now);
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
                    PasswordResetMailContent.body(link, PasswordResetToken.TTL.toMinutes()));
        } catch (RuntimeException e) {
            log.error("重置邮件发送失败：令牌已入库仍可用，用户需重新申请 email={}", to, e);
        }
    }

    private static BusinessException invalidToken() {
        return new BusinessException(ErrorCode.PARAM_INVALID, PasswordResetToken.INVALID_MESSAGE);
    }

    /** DATETIME 只有秒精度，统一截断到秒，保证领域对象与 SQL 使用同一个值。 */
    private static LocalDateTime currentTime() {
        return LocalDateTime.now().withNano(0);
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
