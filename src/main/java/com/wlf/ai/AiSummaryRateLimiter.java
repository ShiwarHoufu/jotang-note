package com.wlf.ai;

import com.wlf.common.BusinessException;
import com.wlf.common.ErrorCode;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI 摘要限频：按用户维度，每个时间窗内至多 {@value #MAX_REQUESTS} 次；本地内存实现。
 * 见《概要设计》§6.8、§7.1。
 *
 * <p><b>它防的不是撞库，是烧钱。</b>每次摘要调用都真金白银地计费，连点按钮或写脚本刷
 * 就一直在花钱——所以这是本接口最需要防的一件事，比 {@code LoginAttemptLimiter}
 * 那条登录限频更硬。
 *
 * <p>实现刻意沿用 {@code LoginAttemptLimiter} 的做法（{@link ConcurrentHashMap} + 惰性过期，
 * 不引入 Redis、也不起清理线程）：MVP 单实例，不存在多实例计数不一致的问题。
 * 与那条仅有的两处差别是——维度换成 {@code userId}（登录态已确定是谁，计数只为控成本，
 * 不需要像账号那样防大小写绕过），以及窗口是<b>固定窗</b>而非「距上次失败」的滑动窗：
 * 这里要控的是「一小时内最多花多少钱」，落在一个可预期的窗口上比随用户操作滑动更好解释。
 */
@Component
public class AiSummaryRateLimiter {

    /** 每个窗口内允许的调用次数（§7.1 的建议值，待实测后按真实费用钉死） */
    private static final int MAX_REQUESTS = 10;

    /** 窗口长度 */
    private static final Duration WINDOW = Duration.ofHours(1);

    private final Map<Long, Window> windows = new ConcurrentHashMap<>();

    /**
     * 取一个名额；已超额则抛 42900 且<b>不消耗名额</b>。
     *
     * <p><b>判断与计数在同一次 {@link ConcurrentHashMap#compute} 里完成</b>，不是「先查再记」。
     * 分成两步的话，两个并发请求可以同时读到「还没满」然后各记一次，把上限顶穿——
     * 这与 {@code LoginAttemptLimiter} 用 {@code compute} 原子替换 {@code Attempt}
     * 是同一个理由。抛异常时映射不变（{@code compute} 的约定），所以被拒的请求不计入。
     *
     * <p>调用点在「本地校验之后、真正调用模型之前」：参数不合法不该白白吃掉一次配额。
     *
     * @throws BusinessException 42900 已超额，data 由 {@code BusinessException} 传空
     *                            （剩余时间通过文案表达，不单独占一个 data 字段）
     */
    public void requireSlot(Long userId) {
        Instant now = Instant.now();
        windows.compute(userId, (key, existing) -> {
            Window base = (existing == null || existing.isExpired(now)) ? Window.fresh(now) : existing;
            if (base.count() >= MAX_REQUESTS) {
                throw new BusinessException(ErrorCode.AI_RATE_LIMITED,
                        "生成过于频繁，请 %d 分钟后再试".formatted(base.remainingMinutes(now)));
            }
            return new Window(base.count() + 1, base.windowStart());
        });
    }

    /**
     * 一个固定窗口内的调用次数。字段 immutable，靠 {@link ConcurrentHashMap#compute} 原子替换。
     *
     * @param count       窗口内已用次数
     * @param windowStart 窗口起点
     */
    private record Window(int count, Instant windowStart) {

        static Window fresh(Instant now) {
            return new Window(0, now);
        }

        /** 窗口已滑过去 → 下次访问时从零开始，避免久远窗口把用户永久卡住 */
        boolean isExpired(Instant now) {
            return windowStart.plus(WINDOW).isBefore(now);
        }

        /** 距窗口结束还有几分钟，向上取整且至少 1——文案说「请 0 分钟后再试」很怪 */
        long remainingMinutes(Instant now) {
            long seconds = Duration.between(now, windowStart.plus(WINDOW)).toSeconds();
            return Math.max((seconds + 59) / 60, 1);
        }
    }
}
