package com.wlf.user.domain.passwordreset;

import com.wlf.common.BusinessException;
import com.wlf.common.ErrorCode;
import com.wlf.user.domain.passwordreset.model.TokenHash;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordResetTokenTest {

    private static final Long USER_ID = 7L;
    private static final TokenHash TOKEN_HASH = new TokenHash("a".repeat(64));
    private static final LocalDateTime ISSUED_AT = LocalDateTime.of(2026, 1, 1, 12, 0);

    @Test
    void issueComputesExpiryFromTtl() {
        PasswordResetToken token = PasswordResetToken.issue(USER_ID, TOKEN_HASH, ISSUED_AT);

        assertThat(token.id()).isNull();
        assertThat(token.userId()).isEqualTo(USER_ID);
        assertThat(token.tokenHash()).isEqualTo(TOKEN_HASH);
        assertThat(token.expiresAt()).isEqualTo(ISSUED_AT.plus(PasswordResetToken.TTL));
        assertThat(token.usedAt()).isNull();
        assertThat(token.isUsable(ISSUED_AT)).isTrue();
    }

    @Test
    void expiresAtTheBoundary() {
        PasswordResetToken token = PasswordResetToken.issue(USER_ID, TOKEN_HASH, ISSUED_AT);

        assertThat(token.isUsable(token.expiresAt().minusSeconds(1))).isTrue();
        assertThat(token.isUsable(token.expiresAt())).isFalse();
        assertThat(token.isUsable(token.expiresAt().plusSeconds(1))).isFalse();
    }

    @Test
    void consumeMarksUsedOnce() {
        PasswordResetToken token = PasswordResetToken.issue(USER_ID, TOKEN_HASH, ISSUED_AT);
        LocalDateTime consumedAt = ISSUED_AT.plusMinutes(5);

        token.consume(consumedAt);

        assertThat(token.usedAt()).isEqualTo(consumedAt);
        assertThat(token.isUsable(consumedAt.plusSeconds(1))).isFalse();
        assertThatThrownBy(() -> token.consume(consumedAt.plusSeconds(1)))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.PARAM_INVALID));
    }

    @Test
    void cannotConsumeExpiredToken() {
        PasswordResetToken token = PasswordResetToken.issue(USER_ID, TOKEN_HASH, ISSUED_AT);

        assertThatThrownBy(() -> token.consume(token.expiresAt()))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.PARAM_INVALID));
    }

    @Test
    void reconstitutesPersistedState() {
        LocalDateTime usedAt = ISSUED_AT.plusMinutes(1);
        PasswordResetToken token = PasswordResetToken.reconstitute(
                3L, USER_ID, TOKEN_HASH, ISSUED_AT.plus(PasswordResetToken.TTL), usedAt);

        assertThat(token.id()).isEqualTo(3L);
        assertThat(token.userId()).isEqualTo(USER_ID);
        assertThat(token.usedAt()).isEqualTo(usedAt);
        assertThat(token.isUsable(ISSUED_AT.plusMinutes(2))).isFalse();
    }

    @Test
    void tokenHashMustBeLowercaseSha256Hex() {
        assertThat(new TokenHash("0123456789abcdef".repeat(4)).value()).hasSize(64);

        assertThatThrownBy(() -> new TokenHash(null))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.SERVER_ERROR));
        assertThatThrownBy(() -> new TokenHash("a".repeat(63)))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.SERVER_ERROR));
        assertThatThrownBy(() -> new TokenHash("A".repeat(64)))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.SERVER_ERROR));
        assertThatThrownBy(() -> new TokenHash("g".repeat(64)))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.SERVER_ERROR));
    }
}
