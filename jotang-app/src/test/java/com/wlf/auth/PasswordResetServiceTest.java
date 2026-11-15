package com.wlf.auth;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.wlf.catalog.CollegeMapper;
import com.wlf.common.BusinessException;
import com.wlf.common.ErrorCode;
import com.wlf.entity.College;
import com.wlf.entity.PasswordResetToken;
import com.wlf.entity.User;
import com.wlf.mail.MailService;
import com.wlf.user.UserMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * {@link PasswordResetService} 的用例。见《概要设计》§6.6。
 *
 * <p>发信被 {@link MockitoBean} 换成了 mock，所以本类<b>不碰网络</b>：它验的是
 * 「令牌怎么签发、怎么消费、库里留下什么」，不是 SMTP 能不能连上。
 * 真发一封的用例由 {@code @Tag("mail")} 单独覆盖。
 *
 * <p>重点在几条<b>写错了不会当场暴露</b>的规则：
 * <ul>
 *   <li><b>邮件里那串明文，哈希之后必须等于库里的 {@code token_hash}</b>——这一条错了，
 *       用户拿到的链接永远点不开，而库里一切正常、日志一片安静</li>
 *   <li><b>同时刻至多一个未消费令牌</b>：重新申请必须把旧的作废</li>
 *   <li><b>令牌只能消费一次</b>，且过期的不行——判据是那条条件 UPDATE 的影响行数</li>
 *   <li><b>邮箱不存在时什么都不做</b>：不写库、不发信、也不报错（防枚举）</li>
 * </ul>
 *
 * <p>每个用例用自己的邮箱（{@link #uniqueEmail()}）。这不是洁癖：
 * {@link PasswordResetRateLimiter} 是单例 Bean、状态活在整个 Spring 上下文里，
 * 多个用例共用同一个邮箱时，第二个用例会先撞上 42300。
 */
@SpringBootTest
@ActiveProfiles("local")
@Transactional
class PasswordResetServiceTest {

    private static final String MARKER = "ZZTEST-PRS-";
    private static final String NEW_PASSWORD = "Passw0rd!23";

    /** 从邮件正文的链接里抠出明文令牌 */
    private static final Pattern TOKEN_IN_LINK = Pattern.compile("token=([A-Za-z0-9_-]+)");

    @Autowired
    private PasswordResetService passwordResetService;

    @Autowired
    private PasswordResetTokenMapper tokenMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private CollegeMapper collegeMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    /**
     * 发信换成 mock。本类其余用例都不因此改变形状——它们本来就是靠
     * {@code MailService} 这个接缝观察「发了什么、发给谁」，而不是靠真的收到信。
     */
    @MockitoBean
    private MailService mailService;

    @Test
    void forgotWritesAnUnconsumedTokenWhoseHashMatchesTheEmailedToken() {
        User user = insertUser(uniqueEmail());

        passwordResetService.forgot(user.getEmail());

        String rawToken = captureEmailedToken();
        PasswordResetToken row = theOnlyTokenOf(user.getId());

        // 本类最重要的一条：用户手里那串明文，哈希之后就是库里那一行
        assertThat(row.getTokenHash()).isEqualTo(sha256Hex(rawToken));
        assertThat(row.getUsedAt()).isNull();

        LocalDateTime now = LocalDateTime.now();
        assertThat(row.getExpiresAt()).isAfter(now.plus(Duration.ofMinutes(29)));
        assertThat(row.getExpiresAt()).isBefore(now.plus(Duration.ofMinutes(31)));
    }

    /** §6.6：同一用户重新申请即作废其全部旧未用令牌，任意时刻至多一个有效令牌 */
    @Test
    void forgotInvalidatesTheUsersEarlierUnconsumedTokens() {
        User user = insertUser(uniqueEmail());
        insertRawToken(user.getId(), "a".repeat(64), LocalDateTime.now().plusMinutes(30));

        passwordResetService.forgot(user.getEmail());

        assertThat(unconsumedTokensOf(user.getId())).hasSize(1);
    }

    /**
     * 邮箱没注册过时：静默返回。不写任何令牌，也不发信——
     * 但<b>不能抛异常</b>，否则响应本身就泄露了「这个邮箱在不在站上」（§6.6 防枚举）。
     */
    @Test
    void forgotForAnUnknownEmailDoesNothingAndStaysSilent() {
        assertThatCode(() -> passwordResetService.forgot(MARKER + "nobody@example.com"))
                .doesNotThrowAnyException();

        verify(mailService, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void resetConsumesTheTokenAndChangesTheStoredPassword() {
        User user = insertUser(uniqueEmail());
        passwordResetService.forgot(user.getEmail());
        String rawToken = captureEmailedToken();

        passwordResetService.reset(rawToken, NEW_PASSWORD);

        assertThat(passwordEncoder.matches(NEW_PASSWORD, reloadPasswordHash(user.getId()))).isTrue();
        assertThat(theOnlyTokenOf(user.getId()).getUsedAt()).isNotNull();
    }

    /** 一次性：同一个令牌第二次必然 40001，且第二次不该再改一次密码 */
    @Test
    void resetWithTheSameTokenTwiceFailsWith40001() {
        User user = insertUser(uniqueEmail());
        passwordResetService.forgot(user.getEmail());
        String rawToken = captureEmailedToken();
        passwordResetService.reset(rawToken, NEW_PASSWORD);
        String hashAfterFirstReset = reloadPasswordHash(user.getId());

        assertThatThrownBy(() -> passwordResetService.reset(rawToken, "AnotherPassw0rd!"))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.PARAM_INVALID));

        assertThat(reloadPasswordHash(user.getId())).isEqualTo(hashAfterFirstReset);
    }

    @Test
    void resetWithAnExpiredTokenFailsWith40001() {
        User user = insertUser(uniqueEmail());
        String rawToken = "expired-token-of-a-well-formed-shape";
        insertRawToken(user.getId(), sha256Hex(rawToken), LocalDateTime.now().minusMinutes(1));

        assertThatThrownBy(() -> passwordResetService.reset(rawToken, NEW_PASSWORD))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.PARAM_INVALID));
    }

    @Test
    void resetWithAnUnknownTokenFailsWith40001() {
        assertThatThrownBy(() -> passwordResetService.reset("no-such-token-anywhere", NEW_PASSWORD))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.PARAM_INVALID));
    }

    /** 改密成功后该用户其余未消费的令牌一并作废：旧链接不该还能用（§6.6） */
    @Test
    void resetInvalidatesTheUsersRemainingTokens() {
        User user = insertUser(uniqueEmail());
        passwordResetService.forgot(user.getEmail());
        String rawToken = captureEmailedToken();
        insertRawToken(user.getId(), "b".repeat(64), LocalDateTime.now().plusMinutes(30));

        passwordResetService.reset(rawToken, NEW_PASSWORD);

        assertThat(unconsumedTokensOf(user.getId())).isEmpty();
    }

    // ---- helpers ----------------------------------------------------------

    /** 每个用例一个邮箱：限频是单例里的状态，共用邮箱会让后一个用例先撞 42300 */
    private static String uniqueEmail() {
        return MARKER + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
    }

    private User insertUser(String email) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        College college = new College();
        college.setName(MARKER + suffix);
        collegeMapper.insert(college);

        User user = new User();
        user.setUsername(MARKER + suffix);
        user.setEmail(email);
        // 真实 BCrypt 值：reset 之后要能用 matches 验出「密码确实换了」
        user.setPasswordHash(passwordEncoder.encode("OldPassw0rd!"));
        user.setNickname(MARKER + suffix);
        user.setCollegeId(college.getId());
        userMapper.insert(user);
        return user;
    }

    /** 直接插一行令牌，绕过 Service——用来构造「旧令牌」「过期令牌」这类前置状态 */
    private void insertRawToken(Long userId, String tokenHash, LocalDateTime expiresAt) {
        PasswordResetToken token = new PasswordResetToken();
        token.setUserId(userId);
        token.setTokenHash(tokenHash);
        token.setExpiresAt(expiresAt);
        tokenMapper.insert(token);
    }

    /** 从 mock 收到的邮件正文里抠出明文令牌，顺带证明链接确实拼进去了 */
    private String captureEmailedToken() {
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(mailService).send(anyString(), anyString(), body.capture());

        Matcher matcher = TOKEN_IN_LINK.matcher(body.getValue());
        assertThat(matcher.find())
                .as("邮件正文里应当有一条 ?token=… 的重置链接")
                .isTrue();
        return matcher.group(1);
    }

    private PasswordResetToken theOnlyTokenOf(Long userId) {
        List<PasswordResetToken> rows = tokenMapper.selectList(
                Wrappers.<PasswordResetToken>lambdaQuery().eq(PasswordResetToken::getUserId, userId));
        assertThat(rows).hasSize(1);
        return rows.get(0);
    }

    private List<PasswordResetToken> unconsumedTokensOf(Long userId) {
        return tokenMapper.selectList(Wrappers.<PasswordResetToken>lambdaQuery()
                .eq(PasswordResetToken::getUserId, userId)
                .isNull(PasswordResetToken::getUsedAt));
    }

    private String reloadPasswordHash(Long userId) {
        return userMapper.selectById(userId).getPasswordHash();
    }

    /**
     * 独立重算一次 SHA-256，<b>刻意不复用生产代码里的那个私有方法</b>。
     *
     * <p>这里要验的是一条对外的契约：「发出去的那串明文，哈希之后等于库里存的那串」。
     * 若两边共用同一个实现，这段逻辑被改错时会一起错，用例照样绿——
     * 而它恰恰是本类最需要盯住的一条。
     */
    private static String sha256Hex(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
