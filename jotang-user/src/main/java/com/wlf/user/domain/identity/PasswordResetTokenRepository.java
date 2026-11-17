package com.wlf.user.domain.identity;

import java.util.Optional;

/**
 * 密码重置令牌的持久化端口。
 */
public interface PasswordResetTokenRepository {

    /** 作废某用户当前全部未消费的令牌。 */
    void invalidateAllUnused(Long userId);

    /** 签发一枚令牌。 */
    void insertToken(Long userId, String tokenHash, long ttlMinutes);

    /** 按令牌哈希查归属。 */
    Optional<Long> findUserIdByTokenHash(String tokenHash);

    /** @return true 表示本次消费成功；false 表示不存在、已用过或已过期 */
    boolean consume(String tokenHash);
}
