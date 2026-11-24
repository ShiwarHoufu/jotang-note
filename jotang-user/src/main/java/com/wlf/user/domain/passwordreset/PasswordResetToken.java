package com.wlf.user.domain.passwordreset;

import com.wlf.common.BusinessException;
import com.wlf.common.ErrorCode;
import com.wlf.user.domain.passwordreset.model.TokenHash;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 密码重置令牌聚合根：一次性、过期与消费语义都在这里。
 *
 * <p>时间由调用方传入，签发与消费使用同一个时钟，避免应用与数据库两套口径。
 */
public final class PasswordResetToken {

    public static final Duration TTL = Duration.ofMinutes(30);
    public static final String INVALID_MESSAGE =
            "重置链接无效、已过期或已被使用（若收到多封邮件，请使用最新的一封）";

    private Long id;
    private final Long userId;
    private final TokenHash tokenHash;
    private final LocalDateTime expiresAt;
    private LocalDateTime usedAt;

    private PasswordResetToken(Long id, Long userId, TokenHash tokenHash,
                               LocalDateTime expiresAt, LocalDateTime usedAt) {
        this.id = id;
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.usedAt = usedAt;
    }

    public static PasswordResetToken issue(Long userId, TokenHash tokenHash, LocalDateTime issuedAt) {
        Objects.requireNonNull(userId);
        Objects.requireNonNull(tokenHash);
        Objects.requireNonNull(issuedAt);
        return new PasswordResetToken(null, userId, tokenHash, issuedAt.plus(TTL), null);
    }

    /** 仅供仓储重建。 */
    public static PasswordResetToken reconstitute(Long id, Long userId, TokenHash tokenHash,
                                                  LocalDateTime expiresAt, LocalDateTime usedAt) {
        return new PasswordResetToken(id, userId, tokenHash, expiresAt, usedAt);
    }

    public boolean isUsable(LocalDateTime now) {
        return usedAt == null && expiresAt.isAfter(now);
    }

    /** 标记消费；真正的一次性由仓储的条件更新兜底。 */
    public void consume(LocalDateTime now) {
        if (!isUsable(now)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, INVALID_MESSAGE);
        }
        this.usedAt = now;
    }

    public Long id() {
        return id;
    }

    public Long userId() {
        return userId;
    }

    public TokenHash tokenHash() {
        return tokenHash;
    }

    public LocalDateTime expiresAt() {
        return expiresAt;
    }

    public LocalDateTime usedAt() {
        return usedAt;
    }
}
