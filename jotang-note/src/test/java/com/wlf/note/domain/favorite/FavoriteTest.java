package com.wlf.note.domain.favorite;

import com.wlf.common.BusinessException;
import com.wlf.common.ErrorCode;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FavoriteTest {

    private static void assertError(Runnable action, ErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(expected));
    }

    @Test
    void createCopiesIds() {
        Favorite favorite = Favorite.create(42L, 7L);

        assertThat(favorite.id()).isNull();
        assertThat(favorite.userId()).isEqualTo(42L);
        assertThat(favorite.noteId()).isEqualTo(7L);
        assertThat(favorite.createdAt()).isNull();
    }

    @Test
    void createRejectsMissingIds() {
        assertError(() -> Favorite.create(null, 7L), ErrorCode.PARAM_INVALID);
        assertError(() -> Favorite.create(42L, null), ErrorCode.PARAM_INVALID);
        assertError(() -> Favorite.create(0L, 7L), ErrorCode.PARAM_INVALID);
        assertError(() -> Favorite.create(42L, 0L), ErrorCode.PARAM_INVALID);
    }

    @Test
    void assignIdOnlyOnce() {
        Favorite favorite = Favorite.create(42L, 7L);

        favorite.assignId(100L);
        assertThat(favorite.id()).isEqualTo(100L);

        assertError(() -> favorite.assignId(101L), ErrorCode.SERVER_ERROR);
    }

    @Test
    void reconstituteRestoresCreatedAt() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 24, 12, 0);

        Favorite favorite = Favorite.reconstitute(1L, 42L, 7L, now);

        assertThat(favorite.id()).isEqualTo(1L);
        assertThat(favorite.createdAt()).isEqualTo(now);
    }
}
