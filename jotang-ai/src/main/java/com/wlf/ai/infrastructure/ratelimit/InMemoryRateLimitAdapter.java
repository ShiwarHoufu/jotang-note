package com.wlf.ai.infrastructure.ratelimit;

import com.wlf.ai.application.port.out.AiQuota;
import com.wlf.ai.application.port.out.RateLimitPolicy;
import com.wlf.ai.application.port.out.RateLimitPort;
import com.wlf.common.BusinessException;
import com.wlf.common.ErrorCode;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 按用户、按用途的固定窗口限频；本地内存实现。 */
public class InMemoryRateLimitAdapter implements RateLimitPort {

    private final Map<AiQuota, RateLimitPolicy> policies;

    private final Map<Key, Window> windows = new ConcurrentHashMap<>();

    public InMemoryRateLimitAdapter(Map<AiQuota, RateLimitPolicy> policies) {
        this.policies = Map.copyOf(policies);
    }

    @Override
    public void acquire(Long userId, AiQuota quota) {
        RateLimitPolicy policy = policies.get(quota);
        if (policy == null) {
            throw new IllegalArgumentException("缺少限流配置：" + quota);
        }

        Instant now = Instant.now();
        // 判断与计数在同一次 compute 里完成，避免并发请求同时读到「还没满」
        windows.compute(new Key(userId, quota), (key, existing) -> {
            Window base = (existing == null || existing.isExpired(now, policy.window()))
                    ? Window.fresh(now)
                    : existing;
            if (base.count() >= policy.maxRequests()) {
                throw new BusinessException(ErrorCode.AI_RATE_LIMITED,
                        "%s过于频繁，请 %d 分钟后再试"
                                .formatted(policy.action(), base.remainingMinutes(now, policy.window())));
            }
            return new Window(base.count() + 1, base.windowStart());
        });
    }

    private record Key(Long userId, AiQuota quota) {
    }

    /** 一个固定窗口内的调用次数。字段 immutable，靠 compute 原子替换。 */
    private record Window(int count, Instant windowStart) {

        static Window fresh(Instant now) {
            return new Window(0, now);
        }

        boolean isExpired(Instant now, Duration window) {
            return windowStart.plus(window).isBefore(now);
        }

        /** 距窗口结束还有几分钟，向上取整且至少 1。 */
        long remainingMinutes(Instant now, Duration window) {
            long seconds = Duration.between(now, windowStart.plus(window)).toSeconds();
            return Math.max((seconds + 59) / 60, 1);
        }
    }
}
