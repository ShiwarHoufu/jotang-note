package com.wlf.ai;

import com.wlf.common.BusinessException;
import com.wlf.common.ErrorCode;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link AiRateLimiter} 的单元测试：纯对象，不起 Spring 上下文，也不碰时钟。
 *
 * <p>固定窗口在测试里不推进时间——本类没有可注入的时钟，所以只验「窗口内的行为」：
 * 额度、用户隔离、实例隔离、以及超额后的错误码。窗口翻篇由 {@code Window#isExpired}
 * 的构造决定，不在这里假装验证。
 */
class AiRateLimiterTest {

    /** 与 {@code AiConfig#aiSummaryRateLimiter} 对齐（《概要设计》§7.1 的建议值） */
    private static final int MAX_REQUESTS = 10;

    private final AiRateLimiter limiter = new AiRateLimiter(MAX_REQUESTS, Duration.ofHours(1), "生成");

    @Test
    void requestsUpToTheLimitAreAllowed() {
        assertThatCode(() -> {
            for (int i = 0; i < MAX_REQUESTS; i++) {
                limiter.requireSlot(1L);
            }
        }).doesNotThrowAnyException();
    }

    /** 第 11 次超额，且必须是 42900——429 与 500 对前端的含义完全不同（§5.7） */
    @Test
    void requestBeyondTheLimitIsRejectedWithRateLimitCode() {
        for (int i = 0; i < MAX_REQUESTS; i++) {
            limiter.requireSlot(1L);
        }

        assertThatThrownBy(() -> limiter.requireSlot(1L))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.AI_RATE_LIMITED));
    }

    /**
     * 被拒的请求<b>不消耗额度</b>：{@code ConcurrentHashMap#compute} 里抛异常时映射不变。
     *
     * <p>若哪天改成「先查再记」，这条会红——那种写法下超额的那次也会把计数推上去。
     */
    @Test
    void rejectedRequestDoesNotConsumeAQuota() {
        for (int i = 0; i < MAX_REQUESTS; i++) {
            limiter.requireSlot(1L);
        }
        assertThatThrownBy(() -> limiter.requireSlot(1L)).isInstanceOf(BusinessException.class);

        // 另一个用户不受影响：额度是按 userId 隔离的，不是全站共享
        assertThatCode(() -> limiter.requireSlot(2L)).doesNotThrowAnyException();
    }

    /** 维度是 userId：一个人的超额不该影响别人，这是「按用户计费」的落点 */
    @Test
    void quotaIsPerUser() {
        for (int i = 0; i < MAX_REQUESTS; i++) {
            limiter.requireSlot(100L);
        }
        assertThatThrownBy(() -> limiter.requireSlot(100L)).isInstanceOf(BusinessException.class);
        assertThatCode(() -> limiter.requireSlot(200L)).doesNotThrowAnyException();
    }

    /**
     * <b>两个实例各算各的额度</b>——这正是摘要与对话不共用一个 Bean 的理由：
     * 共用一个桶的话，「点满摘要」会把聊天一起卡死，而两者的代价差一个数量级。
     */
    @Test
    void instancesDoNotShareTheirQuota() {
        AiRateLimiter chat = new AiRateLimiter(MAX_REQUESTS, Duration.ofHours(1), "对话");
        for (int i = 0; i < MAX_REQUESTS; i++) {
            limiter.requireSlot(1L);
        }
        assertThatThrownBy(() -> limiter.requireSlot(1L)).isInstanceOf(BusinessException.class);

        assertThatCode(() -> chat.requireSlot(1L)).doesNotThrowAnyException();
    }

    /** 被拒文案的主语来自装配：两条接口共用一套窗口簿记，但对用户得各说各的话 */
    @Test
    void rejectionMessageCarriesTheConfiguredAction() {
        for (int i = 0; i < MAX_REQUESTS; i++) {
            limiter.requireSlot(1L);
        }

        assertThatThrownBy(() -> limiter.requireSlot(1L)).hasMessageContaining("生成过于频繁");
    }
}
