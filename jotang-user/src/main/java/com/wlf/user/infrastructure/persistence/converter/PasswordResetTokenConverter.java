package com.wlf.user.infrastructure.persistence.converter;

import com.wlf.user.domain.passwordreset.PasswordResetToken;
import com.wlf.user.domain.passwordreset.model.TokenHash;
import com.wlf.user.infrastructure.persistence.po.PasswordResetTokenPO;
import org.springframework.stereotype.Component;

/** PasswordResetTokenPO 与令牌聚合之间的转换。 */
@Component
public class PasswordResetTokenConverter {

    public PasswordResetToken toDomain(PasswordResetTokenPO po) {
        return PasswordResetToken.reconstitute(
                po.getId(),
                po.getUserId(),
                new TokenHash(po.getTokenHash()),
                po.getExpiresAt(),
                po.getUsedAt()
        );
    }
}
