package com.wlf.ai;

import com.wlf.common.BusinessException;
import com.wlf.common.ErrorCode;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI 接口限频：按用户维度；本地内存实现。
 *
 * <b>对话与摘要两者各持一个实例、各算各的额度</b>
 *
 * <p>实现沿用 {@code LoginAttemptLimiter} / {@code PasswordResetRateLimiter} 的做法
 * （{@link ConcurrentHashMap} + 惰性过期，不引入 Redis、也不起清理线程）：MVP 单实例，
 * 不存在多实例计数不一致的问题。与那两条不同的是维度取 {@code userId}——登录态已确定是谁，
 * 计数只为控成本，不需要像账号那样防大小写绕过；窗口也统一是<b>固定窗</b>
 */
public class AiRateLimiter {

    private final int maxRequests;
    private final Duration window;

    /** 被拒文案的主语（「生成」/「对话」）。后缀由本类补，因为它才是知道还要等多久的那个 */
    private final String action;

    private final Map<Long, Window> windows = new ConcurrentHashMap<>();

    /**
     * @param action 被拒文案的主语，如「生成」「对话」；完整文案是
     *               「{@code action}过于频繁，请 N 分钟后再试」
     */
    public AiRateLimiter(int maxRequests, Duration window, String action) {
        this.maxRequests = maxRequests;
        this.window = window;
        this.action = action;
    }

    /**
     * 取一个名额；已超额则抛 42900 且<b>不消耗名额</b>。
     *
     * <p><b>判断与计数在同一次 {@link ConcurrentHashMap#compute} 里完成</b>，不是「先查再记」。
     * 分成两步的话，两个并发请求可以同时读到「还没满」然后各记一次，把上限顶穿——
     * 这与 {@code LoginAttemptLimiter} 用 {@code compute} 原子替换 {@code Attempt}
     * 是同一个理由。抛异常时映射不变（{@code compute} 的约定），所以被拒的请求不计入。
     *
     * @throws BusinessException 42900 已超额，data 由 {@code BusinessException} 传空
     *                            （剩余时间通过文案表达，不单独占一个 data 字段）
     */
    public void requireSlot(Long userId) {
        Instant now = Instant.now();
        windows.compute(userId, (key, existing) -> {
            Window base = (existing == null || existing.isExpired(now, window)) ? Window.fresh(now) : existing;
            if (base.count() >= maxRequests) {
                throw new BusinessException(ErrorCode.AI_RATE_LIMITED,
                        "%s过于频繁，请 %d 分钟后再试".formatted(action, base.remainingMinutes(now, window)));
            }
            return new Window(base.count() + 1, base.windowStart());
        });
    }

    /**
     * 一个固定窗口内的调用次数。字段 immutable，靠 {@link ConcurrentHashMap#compute} 原子替换。
     *
     * <p>窗口长度不在这里存——它是每个实例一份的配置，由外面传进来，免得复制进每一行记录。
     *
     * @param count       窗口内已用次数
     * @param windowStart 窗口起点
     */
    private record Window(int count, Instant windowStart) {

        static Window fresh(Instant now) {
            return new Window(0, now);
        }

        /** 窗口已滑过去 → 下次访问时从零开始，避免久远窗口把用户永久卡住 */
        boolean isExpired(Instant now, Duration window) {
            return windowStart.plus(window).isBefore(now);
        }

        /** 距窗口结束还有几分钟，向上取整且至少 1——文案说「请 0 分钟后再试」很怪 */
        long remainingMinutes(Instant now, Duration window) {
            long seconds = Duration.between(now, windowStart.plus(window)).toSeconds();
            return Math.max((seconds + 59) / 60, 1);
        }
    }
}
