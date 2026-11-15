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
 * 找回密码限频：按邮箱维度。
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

        /** 距窗口结束还有几分钟，向上取整且至少 1。*/
        long remainingMinutes(Instant now) {
            return Math.max((remainingSeconds(now) + 59) / 60, 1);
        }

        long remainingSeconds(Instant now) {
            return Math.max(Duration.between(now, windowStart.plus(WINDOW)).toSeconds(), 0);
        }
    }
}
