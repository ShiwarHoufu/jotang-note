package com.wlf.ai.infrastructure.quota;

import com.wlf.common.BusinessException;
import com.wlf.common.ErrorCode;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 按用户、按用途的固定窗口限频；本地内存实现。 */
public class FixedWindowRateLimiter {

    private final Map<AiQuota, QuotaRule> rules;

    private final Map<Key, Window> windows = new ConcurrentHashMap<>();

    public FixedWindowRateLimiter(Map<AiQuota, QuotaRule> rules) {
        this.rules = Map.copyOf(rules);
    }

    /** 取一个配额；超出时抛 42900。 */
    public void acquire(Long userId, AiQuota quota) {
        QuotaRule rule = rules.get(quota);
        if (rule == null) {
            throw new IllegalArgumentException("缺少限流配置：" + quota);
        }

        Instant now = Instant.now();
        // 判断与计数在同一次 compute 里完成，避免并发请求同时读到「还没满」
        windows.compute(new Key(userId, quota), (key, existing) -> {
            Window base = (existing == null || existing.isExpired(now, rule.window()))
                    ? Window.fresh(now)
                    : existing;
            if (base.count() >= rule.maxRequests()) {
                throw new BusinessException(ErrorCode.AI_RATE_LIMITED,
                        "%s过于频繁，请 %d 分钟后再试"
                                .formatted(rule.action(), base.remainingMinutes(now, rule.window())));
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
