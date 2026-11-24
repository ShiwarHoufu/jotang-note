package com.wlf.note.domain.note;

import com.wlf.common.BusinessException;
import com.wlf.common.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NoteAccessPolicyTest {

    private static void assertError(Runnable action, ErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(expected));
    }

    @Test
    void requireUploaderAcceptsOnlyMatches() {
        assertThatCode(() -> NoteAccessPolicy.requireUploader(42L, 42L))
                .doesNotThrowAnyException();
        assertError(() -> NoteAccessPolicy.requireUploader(42L, 99L), ErrorCode.FORBIDDEN);
    }

    @Test
    void requireEditableRejectsDeletedOnly() {
        assertThatCode(() -> NoteAccessPolicy.requireEditable(NoteStatus.ONLINE))
                .doesNotThrowAnyException();
        assertThatCode(() -> NoteAccessPolicy.requireEditable(NoteStatus.OFFLINE))
                .doesNotThrowAnyException();
        assertError(() -> NoteAccessPolicy.requireEditable(NoteStatus.DELETED),
                ErrorCode.NOTE_UNAVAILABLE);
    }

    @Test
    void requireOnlineRejectsOtherStates() {
        assertThatCode(() -> NoteAccessPolicy.requireOnline(NoteStatus.ONLINE))
                .doesNotThrowAnyException();
        assertError(() -> NoteAccessPolicy.requireOnline(NoteStatus.OFFLINE),
                ErrorCode.NOTE_UNAVAILABLE);
        assertError(() -> NoteAccessPolicy.requireOnline(NoteStatus.DELETED),
                ErrorCode.NOTE_UNAVAILABLE);
    }

    @Test
    void requireNotDeletedRejectsDeletedOnly() {
        assertThatCode(() -> NoteAccessPolicy.requireNotDeleted(NoteStatus.ONLINE))
                .doesNotThrowAnyException();
        assertThatCode(() -> NoteAccessPolicy.requireNotDeleted(NoteStatus.OFFLINE))
                .doesNotThrowAnyException();
        assertError(() -> NoteAccessPolicy.requireNotDeleted(NoteStatus.DELETED),
                ErrorCode.NOTE_UNAVAILABLE);
    }

    @Test
    void requireVisibleFollowsStatusAndIdentity() {
        assertThatCode(() -> NoteAccessPolicy.requireVisible(NoteStatus.ONLINE, 42L, 99L, false))
                .doesNotThrowAnyException();
        assertThatCode(() -> NoteAccessPolicy.requireVisible(NoteStatus.OFFLINE, 42L, 42L, false))
                .doesNotThrowAnyException();
        assertThatCode(() -> NoteAccessPolicy.requireVisible(NoteStatus.OFFLINE, 42L, 99L, true))
                .doesNotThrowAnyException();

        assertError(() -> NoteAccessPolicy.requireVisible(NoteStatus.OFFLINE, 42L, 99L, false),
                ErrorCode.NOTE_UNAVAILABLE);
        assertError(() -> NoteAccessPolicy.requireVisible(NoteStatus.DELETED, 42L, 42L, true),
                ErrorCode.NOTE_UNAVAILABLE);
    }
}
