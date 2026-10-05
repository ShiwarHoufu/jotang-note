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
 * 登录限频：仅账号维度，连续失败 5 次锁定 10 分钟；本地内存实现。
 * 见《概要设计》§6.3（决策 D3）。
 *
 * <p>只按账号、不按 IP：按 IP 会误伤同一校园网出口的整批用户（D3）。
 *
 * <p>不引入 Redis 与定时任务，靠 {@link ConcurrentHashMap} + 惰性过期（《技术选型》§5）。
 * 单实例部署，不存在多实例状态不一致问题。
 *
 * <p>已知限制（§8.4-4）：应用重启后锁定状态丢失，MVP 接受。
 */
@Component
public class LoginAttemptLimiter {

    private static final int MAX_FAILURES = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(10);

    /**
     * key 用小写：MySQL 默认排序规则 {@code utf8mb4_0900_ai_ci} 大小写不敏感，
     * 登录名大小写本就视为同一账号，限频的计数口径必须与之对齐，
     * 否则大小写换着试就能绕过锁定。
     */
    private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();

    /** 登录校验前调用：仍在锁定期则直接拒绝，data 回传剩余秒数供前端倒计时 */
    public void ensureNotLocked(String username) {
        String key = normalize(username);
        Attempt attempt = attempts.get(key);
        if (attempt == null) {
            return;
        }

        Instant now = Instant.now();
        long remainingSeconds = attempt.remainingLockSeconds(now);
        if (remainingSeconds > 0) {
            throw new BusinessException(ErrorCode.ACCOUNT_LOCKED,
                    "账号已锁定，请 %d 分钟后重试".formatted((remainingSeconds + 59) / 60),
                    remainingSeconds);
        }

        // 惰性过期：窗口已过的记录等到下次访问再清，避免额外起清理线程。
        // 判据必须是 isExpired——不能用「剩余锁定秒数为 0」，那个条件在「尚未锁定」时同样成立，
        // 会把还没攒够次数的失败记录一并删掉，导致计数永远涨不到阈值、账号锁不住。
        if (attempt.isExpired(now)) {
            attempts.remove(key, attempt);
        }
    }

    /** 登录失败后调用；累计到阈值即开始锁定 */
    public void recordFailure(String username) {
        Instant now = Instant.now();
        attempts.compute(normalize(username), (key, existing) -> {
            Attempt base = (existing == null || existing.isExpired(now)) ? Attempt.fresh() : existing;
            int failures = base.failures() + 1;
            Instant lockedUntil = failures >= MAX_FAILURES ? now.plus(LOCK_DURATION) : null;
            return new Attempt(failures, lockedUntil, now);
        });
    }

    /** 登录成功后调用，清零计数 */
    public void reset(String username) {
        attempts.remove(normalize(username));
    }

    private static String normalize(String username) {
        //使用无地区中立规则做大小写转换
        return username.trim().toLowerCase(Locale.ROOT);
    }


    /**
     * 一次连续失败轨迹。字段 immutable，靠 {@link ConcurrentHashMap#compute} 原子替换。
     *
     * @param failures      连续失败次数
     * @param lockedUntil   锁定截止时刻；null 表示未触发锁定
     * @param lastFailureAt 最近一次失败时刻，用于让久远的失败自然失效
     */
    private record Attempt(int failures, Instant lockedUntil, Instant lastFailureAt) {

        static Attempt fresh() {
            return new Attempt(0, null, Instant.EPOCH);
        }

        /** 锁已到期，或失败记录已滑出锁定窗口 → 视为从头开始，避免几个月前攒的失败把账号锁死 */
        boolean isExpired(Instant now) {
            Instant deadline = lockedUntil != null ? lockedUntil : lastFailureAt.plus(LOCK_DURATION);
            return deadline.isBefore(now);
        }

        long remainingLockSeconds(Instant now) {
            if (lockedUntil == null) {
                return 0;
            }
            return Math.max(Duration.between(now, lockedUntil).toSeconds(), 0);
        }
    }
}
