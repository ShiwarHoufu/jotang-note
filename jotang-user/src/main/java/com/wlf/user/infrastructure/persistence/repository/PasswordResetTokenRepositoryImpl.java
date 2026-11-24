package com.wlf.user.infrastructure.persistence.repository;

import com.wlf.user.domain.passwordreset.PasswordResetToken;
import com.wlf.user.domain.passwordreset.PasswordResetTokenRepository;
import com.wlf.user.domain.passwordreset.model.TokenHash;
import com.wlf.user.infrastructure.persistence.converter.PasswordResetTokenConverter;
import com.wlf.user.infrastructure.persistence.mapper.PasswordResetTokenMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 密码重置令牌仓储的 MyBatis 实现。
 */
@Repository
public class PasswordResetTokenRepositoryImpl implements PasswordResetTokenRepository {

    private final PasswordResetTokenMapper tokenMapper;
    private final PasswordResetTokenConverter converter;

    public PasswordResetTokenRepositoryImpl(PasswordResetTokenMapper tokenMapper,
                                            PasswordResetTokenConverter converter) {
        this.tokenMapper = tokenMapper;
        this.converter = converter;
    }

    @Override
    public Optional<PasswordResetToken> findByTokenHash(TokenHash tokenHash) {
        return Optional.ofNullable(tokenMapper.selectByTokenHash(tokenHash.value()))
                .map(converter::toDomain);
    }

    @Override
    public void insert(PasswordResetToken token) {
        tokenMapper.insertToken(token.userId(), token.tokenHash().value(), token.expiresAt());
    }

    @Override
    public boolean consume(TokenHash tokenHash, LocalDateTime now) {
        return tokenMapper.consume(tokenHash.value(), now) > 0;
    }

    @Override
    public void invalidateAllUnused(Long userId, LocalDateTime now) {
        tokenMapper.invalidateAllUnused(userId, now);
    }
}
