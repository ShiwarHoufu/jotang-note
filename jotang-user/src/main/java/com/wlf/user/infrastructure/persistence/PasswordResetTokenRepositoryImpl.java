package com.wlf.user.infrastructure.persistence;

import com.wlf.user.domain.identity.PasswordResetTokenRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 密码重置令牌仓储的 MyBatis 实现。
 */
@Repository
public class PasswordResetTokenRepositoryImpl implements PasswordResetTokenRepository {

    private final PasswordResetTokenMapper tokenMapper;

    public PasswordResetTokenRepositoryImpl(PasswordResetTokenMapper tokenMapper) {
        this.tokenMapper = tokenMapper;
    }

    @Override
    public void invalidateAllUnused(Long userId) {
        tokenMapper.invalidateAllUnused(userId);
    }

    @Override
    public void insertToken(Long userId, String tokenHash, long ttlMinutes) {
        tokenMapper.insertToken(userId, tokenHash, ttlMinutes);
    }

    @Override
    public Optional<Long> findUserIdByTokenHash(String tokenHash) {
        return Optional.ofNullable(tokenMapper.selectUserIdByTokenHash(tokenHash));
    }

    @Override
    public boolean consume(String tokenHash) {
        return tokenMapper.consume(tokenHash) > 0;
    }
}
