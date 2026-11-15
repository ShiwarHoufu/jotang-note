package com.wlf.auth;

import com.wlf.common.BusinessException;
import com.wlf.common.ErrorCode;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link LoginAttemptLimiter} 的单元测试：纯对象，不起 Spring 上下文，也不碰时钟。
 *
 * <p>锁定期内的恢复、「窗口翻篇」依赖真实时间流逝，没有可注入的时钟，
 * 由 {@code Attempt#isExpired} 的形状决定，在《登录》文档的「未覆盖的部分」里如实记着。
 */
class LoginAttemptLimiterTest {

    private final LoginAttemptLimiter limiter = new LoginAttemptLimiter();

    @Test
    void failuresBelowTheThresholdDoNotLock() {
        for (int i = 0; i < 4; i++) {
            limiter.recordFailure("zztest-lal-a");
        }

        assertThatCode(() -> limiter.ensureNotLocked("zztest-lal-a")).doesNotThrowAnyException();
    }

    /** 第 5 次失败触发锁定，且 data 带剩余秒数供前端倒计时 */
    @Test
    void fifthFailureLocksAndCarriesRemainingSeconds() {
        for (int i = 0; i < 5; i++) {
            limiter.recordFailure("zztest-lal-b");
        }

        assertThatThrownBy(() -> limiter.ensureNotLocked("zztest-lal-b"))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.ACCOUNT_LOCKED);
                    assertThat(ex.getData()).isInstanceOf(Long.class);
                    // 锁定 10 分钟，剩余秒数落在 (0, 600]
                    assertThat((Long) ex.getData()).isPositive().isLessThanOrEqualTo(600);
                });
    }

    /** 维度是账号：一个人被锁不该牵连别人 */
    @Test
    void lockoutIsPerAccount() {
        for (int i = 0; i < 5; i++) {
            limiter.recordFailure("zztest-lal-c");
        }

        assertThatThrownBy(() -> limiter.ensureNotLocked("zztest-lal-c"))
                .isInstanceOf(BusinessException.class);
        assertThatCode(() -> limiter.ensureNotLocked("zztest-lal-d")).doesNotThrowAnyException();
    }

    /**
     * key 做 trim + 小写归一：MySQL 的 {@code utf8mb4_0900_ai_ci} 大小写不敏感，
     * 同一账号的大小写变体在库里是同一行，限频口径必须与库一致，
     * 否则换着大小写试就能绕过锁定。若哪天去掉 {@code normalize}，这条会红。
     */
    @Test
    void caseAndWhitespaceVariantsShareTheSameSlot() {
        for (int i = 0; i < 5; i++) {
            limiter.recordFailure("ZZTest-LAL-E");
        }

        assertThatThrownBy(() -> limiter.ensureNotLocked("  zztest-lal-e  "))
                .isInstanceOf(BusinessException.class);
    }

    /**
     * 成功清零计数：4 次失败 → 成功 → 再 4 次失败仍不应锁定。
     * 若 reset 没生效，第 5 次累计失败会在第二批的第 1 次就触发锁定。
     */
    @Test
    void resetClearsTheFailureCount() {
        for (int i = 0; i < 4; i++) {
            limiter.recordFailure("zztest-lal-f");
        }
        limiter.reset("zztest-lal-f");

        for (int i = 0; i < 4; i++) {
            limiter.recordFailure("zztest-lal-f");
        }

        assertThatCode(() -> limiter.ensureNotLocked("zztest-lal-f")).doesNotThrowAnyException();
    }

    /**
     * 并发失败不丢计数、也不多计：靠 {@code ConcurrentHashMap.compute} 的原子替换保证。
     *
     * <p>丢了计数则「恰好 5 次并发失败」有时锁不住，多计则「恰好 4 次」有时误锁——
     * 两个方向都钉住，跑多轮才够灵敏（单轮很容易碰巧对上）。
     */
    @Test
    void concurrentFailuresAreCountedExactly() throws Exception {
        for (int round = 0; round < 200; round++) {
            String four = "zztest-lal-concurrent-4-" + round;
            runConcurrently(4, () -> limiter.recordFailure(four));
            assertThatCode(() -> limiter.ensureNotLocked(four))
                    .as("第 %d 轮：恰好 4 次并发失败不该触发锁定", round)
                    .doesNotThrowAnyException();

            String five = "zztest-lal-concurrent-5-" + round;
            runConcurrently(5, () -> limiter.recordFailure(five));
            assertThatThrownBy(() -> limiter.ensureNotLocked(five))
                    .as("第 %d 轮：5 次并发失败必须触发锁定，计数不能丢", round)
                    .isInstanceOf(BusinessException.class);
        }
    }

    /** 用发令枪让 n 个线程尽量同时调 {@code task}，等全部结束再返回 */
    private static void runConcurrently(int threads, Runnable task) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        try {
            for (int i = 0; i < threads; i++) {
                pool.submit(() -> {
                    try {
                        start.await();
                        task.run();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            done.await(10, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
    }
}
