package com.wlf.user.domain.user.model;

import com.wlf.common.BusinessException;
import com.wlf.common.ErrorCode;
import com.wlf.common.FieldViolation;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserValueObjectsTest {

    @SuppressWarnings("unchecked")
    private static void assertInvalidField(Runnable action, String field) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class, ex -> {
            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.PARAM_INVALID);
            assertThat((List<FieldViolation>) ex.getData())
                    .singleElement()
                    .extracting(FieldViolation::field)
                    .isEqualTo(field);
        });
    }

    @Nested
    class UsernameTest {

        @Test
        void trimsAndKeepsOriginalCase() {
            Username username = new Username("  Alice_01 ");
            assertThat(username.value()).isEqualTo("Alice_01");
            assertThat(username.normalized()).isEqualTo("alice_01");
        }

        @Test
        void rejectsIllegalInput() {
            assertInvalidField(() -> new Username(null), "username");
            assertInvalidField(() -> new Username("ab"), "username");
            assertInvalidField(() -> new Username("a".repeat(33)), "username");
            assertInvalidField(() -> new Username("bad-name"), "username");
            assertInvalidField(() -> new Username("名字"), "username");
        }
    }

    @Nested
    class EmailTest {

        @Test
        void trimsAndLowercases() {
            assertThat(new Email("  Foo@Example.COM ").value()).isEqualTo("foo@example.com");
        }

        @Test
        void rejectsIllegalInput() {
            assertInvalidField(() -> new Email(null), "email");
            assertInvalidField(() -> new Email("foo"), "email");
            assertInvalidField(() -> new Email("a@b@c"), "email");
            assertInvalidField(() -> new Email("a b@c"), "email");
            assertInvalidField(() -> new Email("a@" + "b".repeat(127)), "email");
        }
    }

    @Nested
    class PasswordTest {

        @Test
        void acceptsBoundaryLengths() {
            assertThat(new RawPassword("12345678").value()).isEqualTo("12345678");
            assertThat(new RawPassword("a".repeat(64)).value()).hasSize(64);
        }

        @Test
        void rejectsIllegalRawPassword() {
            assertInvalidField(() -> new RawPassword(null), "password");
            assertInvalidField(() -> new RawPassword("1234567"), "password");
            assertInvalidField(() -> new RawPassword("a".repeat(65)), "password");
        }

        @Test
        void masksToString() {
            assertThat(new RawPassword("super-secret").toString()).doesNotContain("super-secret");
            assertThat(new PasswordHash("$2a$10$abcdefghijklmnopqrstuv").toString())
                    .doesNotContain("$2a$10$abcdefghijklmnopqrstuv");
        }

        @Test
        void rejectsBlankHashAsServerError() {
            assertThatThrownBy(() -> new PasswordHash(" "))
                    .isInstanceOfSatisfying(BusinessException.class,
                            ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.SERVER_ERROR));
        }
    }

    @Nested
    class NicknameTest {

        @Test
        void trimsAndFallsBackToUsername() {
            assertThat(new Nickname("  小明  ").value()).isEqualTo("小明");
            assertThat(Nickname.fromUsername(new Username("Alice")).value()).isEqualTo("Alice");
        }

        @Test
        void rejectsBlankOrTooLong() {
            assertInvalidField(() -> new Nickname(null), "nickname");
            assertInvalidField(() -> new Nickname("   "), "nickname");
            assertInvalidField(() -> new Nickname("a".repeat(33)), "nickname");
        }

        @Test
        void acceptsMaxLength() {
            assertThat(new Nickname("a".repeat(32)).value()).hasSize(32);
        }
    }

    @Nested
    class CollegeIdTest {

        @Test
        void acceptsPositiveId() {
            assertThat(new CollegeId(1L).value()).isEqualTo(1L);
        }

        @Test
        void rejectsMissingOrNonPositive() {
            assertInvalidField(() -> new CollegeId(null), "collegeId");
            assertInvalidField(() -> new CollegeId(0L), "collegeId");
            assertInvalidField(() -> new CollegeId(-1L), "collegeId");
        }
    }

    @Nested
    class AvatarKeyTest {

        private static final String KEY = "avatars/42/123e4567-e89b-12d3-a456-426614174000.png";

        @Test
        void acceptsGeneratedKey() {
            assertThat(new AvatarKey(KEY).value()).isEqualTo(KEY);
        }

        @Test
        void belongsToOwnerOnly() {
            AvatarKey key = new AvatarKey(KEY);
            assertThat(key.belongsTo(42L)).isTrue();
            assertThat(key.belongsTo(43L)).isFalse();
            assertThat(key.belongsTo(null)).isFalse();
        }

        @Test
        void rejectsIllegalKey() {
            assertInvalidField(() -> new AvatarKey(null), "avatar");
            assertInvalidField(() -> new AvatarKey("notes/42/x.png"), "avatar");
            assertInvalidField(() -> new AvatarKey("avatars/42/not-a-uuid.png"), "avatar");
            assertInvalidField(() -> new AvatarKey("avatars/42/123e4567-e89b-12d3-a456-426614174000.svg"), "avatar");
        }
    }

    @Nested
    class StatusTest {

        @Test
        void mapsCode() {
            assertThat(UserStatus.fromCode(1)).isEqualTo(UserStatus.ACTIVE);
            assertThat(UserStatus.fromCode(0)).isEqualTo(UserStatus.DISABLED);
            assertThat(Role.ADMIN.isAdmin()).isTrue();
            assertThat(Role.USER.isAdmin()).isFalse();
        }

        @Test
        void rejectsUnknownCode() {
            assertThatThrownBy(() -> UserStatus.fromCode(2))
                    .isInstanceOfSatisfying(BusinessException.class,
                            ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.SERVER_ERROR));
        }
    }
}
