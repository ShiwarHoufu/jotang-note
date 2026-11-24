package com.wlf.user.domain.user;

import com.wlf.common.BusinessException;
import com.wlf.common.ErrorCode;
import com.wlf.common.FieldViolation;
import com.wlf.user.domain.user.model.AvatarKey;
import com.wlf.user.domain.user.model.CollegeId;
import com.wlf.user.domain.user.model.Email;
import com.wlf.user.domain.user.model.Nickname;
import com.wlf.user.domain.user.model.PasswordHash;
import com.wlf.user.domain.user.model.RawPassword;
import com.wlf.user.domain.user.model.Role;
import com.wlf.user.domain.user.model.UserStatus;
import com.wlf.user.domain.user.model.Username;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserTest {

    private static final Username USERNAME = new Username("Alice");
    private static final Email EMAIL = new Email("Alice@Example.com");
    private static final CollegeId COLLEGE_ID = new CollegeId(7L);
    private static final PasswordHash PASSWORD_HASH = new PasswordHash("H:password123");

    private static final PasswordHasher HASHER = new PasswordHasher() {
        @Override
        public PasswordHash hash(RawPassword rawPassword) {
            return new PasswordHash("H:" + rawPassword.value());
        }

        @Override
        public boolean matches(RawPassword rawPassword, PasswordHash passwordHash) {
            return passwordHash.value().equals("H:" + rawPassword.value());
        }
    };

    private static User registered() {
        return User.register(USERNAME, EMAIL, PASSWORD_HASH, COLLEGE_ID);
    }

    @Test
    void registerAppliesDefaults() {
        User user = registered();

        assertThat(user.id()).isNull();
        assertThat(user.username().value()).isEqualTo("Alice");
        assertThat(user.email().value()).isEqualTo("alice@example.com");
        assertThat(user.nickname().value()).isEqualTo("Alice");
        assertThat(user.collegeId().value()).isEqualTo(7L);
        assertThat(user.role()).isEqualTo(Role.USER);
        assertThat(user.status()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.avatar()).isNull();
        assertThat(user.isAdmin()).isFalse();
    }

    @Test
    void verifiesPasswordThroughHasher() {
        assertThat(registered().verifyPassword(new RawPassword("password123"), HASHER)).isTrue();
        assertThat(registered().verifyPassword(new RawPassword("wrongpassword"), HASHER)).isFalse();
    }

    @Test
    void changesProfile() {
        User user = registered();

        user.changeProfile(new Nickname("小明"), new CollegeId(8L));

        assertThat(user.nickname().value()).isEqualTo("小明");
        assertThat(user.collegeId().value()).isEqualTo(8L);
    }

    @Test
    void changesPassword() {
        User user = registered();

        user.changePassword(new PasswordHash("H:newpassword"));

        assertThat(user.verifyPassword(new RawPassword("newpassword"), HASHER)).isTrue();
        assertThat(user.verifyPassword(new RawPassword("password123"), HASHER)).isFalse();
    }

    @Test
    void disabledUserCannotLoginUntilEnabled() {
        User user = registered();

        user.disable();
        assertThatThrownBy(user::ensureCanLogin)
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        user.enable();
        assertThatCode(user::ensureCanLogin).doesNotThrowAnyException();
    }

    @Test
    void avatarMustBelongToOwner() {
        User user = registered();
        user.assignId(42L);

        AvatarKey ownKey = new AvatarKey("avatars/42/123e4567-e89b-12d3-a456-426614174000.png");
        user.changeAvatar(ownKey);
        assertThat(user.avatar()).isEqualTo(ownKey);

        assertThatThrownBy(() -> user.changeAvatar(
                new AvatarKey("avatars/43/123e4567-e89b-12d3-a456-426614174000.png")))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.PARAM_INVALID);
                    assertThat(avatarViolationField(ex)).isEqualTo("avatar");
                });
    }

    @SuppressWarnings("unchecked")
    private static String avatarViolationField(BusinessException ex) {
        return ((List<FieldViolation>) ex.getData()).getFirst().field();
    }

    @Test
    void assignsIdOnlyOnce() {
        User user = registered();

        user.assignId(1L);
        assertThat(user.id()).isEqualTo(1L);
        assertThatThrownBy(() -> user.assignId(2L)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void reconstitutesPersistedState() {
        User user = User.reconstitute(9L, USERNAME, EMAIL, PASSWORD_HASH,
                new Nickname("小艾"), COLLEGE_ID,
                new AvatarKey("avatars/9/123e4567-e89b-12d3-a456-426614174000.webp"),
                Role.ADMIN, UserStatus.DISABLED);

        assertThat(user.id()).isEqualTo(9L);
        assertThat(user.nickname().value()).isEqualTo("小艾");
        assertThat(user.role()).isEqualTo(Role.ADMIN);
        assertThat(user.status()).isEqualTo(UserStatus.DISABLED);
        assertThat(user.avatar().belongsTo(9L)).isTrue();
    }
}
