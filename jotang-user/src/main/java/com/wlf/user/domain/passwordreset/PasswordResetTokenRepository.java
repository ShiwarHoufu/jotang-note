package com.wlf.user.domain.passwordreset;

import com.wlf.user.domain.passwordreset.model.TokenHash;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 密码重置令牌的持久化端口。
 */
public interface PasswordResetTokenRepository {

    Optional<PasswordResetToken> findByTokenHash(TokenHash tokenHash);

    void insert(PasswordResetToken token);

    /** @return true 表示本次消费成功；false 表示不存在、已用过或已过期 */
    boolean consume(TokenHash tokenHash, LocalDateTime now);

    /** 作废某用户当前全部未消费的令牌。 */
    void invalidateAllUnused(Long userId, LocalDateTime now);
}
