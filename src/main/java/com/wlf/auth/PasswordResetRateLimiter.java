package com.wlf.auth;

import com.wlf.common.BusinessException;
import com.wlf.common.ErrorCode;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 找回密码限频：按邮箱维度
 * 见《概要设计》§6.3、§6.6。
 *
 * <p>实现沿用 {@link com.wlf.ai.AiSummaryRateLimiter} 的做法
 * （{@link ConcurrentHashMap} + 惰性过期，不引入 Redis、也不起清理线程）：MVP 单实例，
 * 不存在多实例计数不一致的问题。与它仅有的两处差别是维度（邮箱 vs 用户 id）与错误码。
 *
 * <p>键要做小写归一，与 {@link LoginAttemptLimiter} 同口径
 *
 * <h2>已知边界：固定窗在窗口交界处会放过一次</h2>
 * <p>这是固定窗（每 5分钟一个桶）而不是「距上次申请满 5 分钟」的滑动窗。
 * 若将来要收紧，改成记录「上次申请时刻」即可，形状与 {@link LoginAttemptLimiter} 一致。
 */
@Component
public class PasswordResetRateLimiter {

    /** 每个窗口内允许的申请次数 */
    private static final int MAX_REQUESTS = 1;

    /** 窗口长度 */
    private static final Duration WINDOW = Duration.ofMinutes(5);

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    /**
     * 取一个名额；已超额则抛 42300 且不消耗名额。
     *
     * <p><b>判断与计数在同一次 {@link ConcurrentHashMap#compute} 里完成</b>，不是「先查再记」。
     * 分成两步的话，两个并发请求可以同时读到「还没满」然后各记一次，把上限顶穿——
     *
     * <p>调用点在「查用户之前」：这个接口的前置成本只有一次内存查表，
     * 而被拒的请求不该往下走到查库与生成令牌——更不该有机会发出邮件。
     *
     * @param email 用户提交的邮箱
     * @throws BusinessException 42300，data 为剩余秒数
     */
    public void requireSlot(String email) {
        String key = normalize(email);
        Instant now = Instant.now();
        windows.compute(key, (k, existing) -> {
            Window base = (existing == null || existing.isExpired(now)) ? Window.fresh(now) : existing;
            if (base.count() >= MAX_REQUESTS) {
                throw new BusinessException(ErrorCode.ACCOUNT_LOCKED,
                        "重置邮件发送过于频繁，请 %d 分钟后重试".formatted(base.remainingMinutes(now)),
                        base.remainingSeconds(now));
            }
            return new Window(base.count() + 1, base.windowStart());
        });
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 一个固定窗口内的申请次数。字段 immutable，靠 {@link ConcurrentHashMap#compute} 原子替换。
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
            return Math.max((remainingSeconds(now) + 59) / 60, 1);
        }

        long remainingSeconds(Instant now) {
            return Math.max(Duration.between(now, windowStart.plus(WINDOW)).toSeconds(), 0);
        }
    }
}
