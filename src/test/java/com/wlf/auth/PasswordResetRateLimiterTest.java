package com.wlf.auth;

import com.wlf.common.BusinessException;
import com.wlf.common.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link PasswordResetRateLimiter} 的单元测试：纯对象，不起 Spring 上下文，也不碰时钟。
 *
 * <p>与 {@code AiRateLimiterTest} 一样，本类<b>不假装验证「窗口翻篇」</b>——
 * 没有可注入的时钟，窗口滑过后的恢复由 {@code Window#isExpired} 的形状决定，
 * 在 {@code 密码找回.md} 的「未覆盖的部分」里如实记着。
 */
class PasswordResetRateLimiterTest {

    private static final String EMAIL = "zztest-prl@example.com";

    private final PasswordResetRateLimiter limiter = new PasswordResetRateLimiter();

    /** 第一次放行。§6.3 的口径是「同一邮箱 5 分钟 1 次」，第一次必然该过 */
    @Test
    void firstRequestIsAllowed() {
        assertThatCode(() -> limiter.requireSlot(EMAIL)).doesNotThrowAnyException();
    }

    /** 窗口内第二次拒绝，且必须是 42300——§6.3 指定复用登录锁定那个码 */
    @Test
    void secondRequestWithinTheWindowIsRejectedWithAccountLocked() {
        limiter.requireSlot(EMAIL);

        assertThatThrownBy(() -> limiter.requireSlot(EMAIL))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.ACCOUNT_LOCKED));
    }

    /**
     * 被拒时 data 里带剩余秒数：{@code LoginAttemptLimiter} 用同一个错误码时就是这么传的，
     * 前端对 42300 已经有读 data 做倒计时的处理。两个接口共用一个码，契约就得一致。
     */
    @Test
    void rejectedRequestCarriesRemainingSecondsForTheCountdown() {
        limiter.requireSlot(EMAIL);

        assertThatThrownBy(() -> limiter.requireSlot(EMAIL))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getData()).isInstanceOf(Long.class);
                    long remaining = (Long) ex.getData();
                    // 窗口 5 分钟，剩余秒数只能落在 (0, 300]
                    assertThat(remaining).isPositive().isLessThanOrEqualTo(300);
                });
    }

    /** 维度是邮箱：一个人被限流不该影响别人 */
    @Test
    void quotaIsPerEmail() {
        limiter.requireSlot("zztest-prl-a@example.com");
        assertThatThrownBy(() -> limiter.requireSlot("zztest-prl-a@example.com"))
                .isInstanceOf(BusinessException.class);

        assertThatCode(() -> limiter.requireSlot("zztest-prl-b@example.com")).doesNotThrowAnyException();
    }

    /**
     * 键做 trim + 小写归一：MySQL 的 {@code utf8mb4_0900_ai_ci} 大小写不敏感，
     * 同一个邮箱的大小写变体在库里是同一行。限频的口径若与库不一致，
     * 换着大小写写就能刷出成倍的邮件——这正是本条要钉住的行为。
     *
     * <p>若哪天去掉 {@code normalize}，这条会红。
     */
    @Test
    void caseAndWhitespaceVariantsShareTheSameSlot() {
        limiter.requireSlot("ZZTest-PRL@Example.com");

        assertThatThrownBy(() -> limiter.requireSlot("  zztest-prl@example.com  "))
                .isInstanceOf(BusinessException.class);
    }
}
