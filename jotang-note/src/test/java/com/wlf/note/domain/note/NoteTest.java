package com.wlf.note.domain.note;

import com.wlf.common.BusinessException;
import com.wlf.common.ErrorCode;
import com.wlf.note.domain.note.model.ContentType;
import com.wlf.note.domain.note.model.CourseId;
import com.wlf.note.domain.note.model.FileSize;
import com.wlf.note.domain.note.model.NoteSummary;
import com.wlf.note.domain.note.model.NoteTitle;
import com.wlf.note.domain.note.model.OriginalFileName;
import com.wlf.note.domain.note.model.StorageKey;
import com.wlf.note.domain.note.model.TeacherName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NoteTest {

    private static final Long UPLOADER_ID = 42L;

    private static NoteFile sampleFile() {
        return NoteFile.attach(
                new StorageKey("notes/2026/11/123e4567-e89b-12d3-a456-426614174000.md"),
                new OriginalFileName("笔记.md"),
                new FileSize(1024),
                new ContentType("text/markdown"));
    }

    private static Note sampleNote() {
        return Note.upload(UPLOADER_ID,
                new CourseId(1L),
                new NoteTitle("标题"),
                new NoteSummary("简介"),
                new TeacherName("王老师"),
                sampleFile(),
                Set.of(7L, 8L));
    }

    private static void assertError(Runnable action, ErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(expected));
    }

    @Test
    void uploadStartsOnlineWithZeroCounters() {
        Note note = sampleNote();

        assertThat(note.id()).isNull();
        assertThat(note.uploaderId()).isEqualTo(UPLOADER_ID);
        assertThat(note.status()).isEqualTo(NoteStatus.ONLINE);
        assertThat(note.viewCount()).isZero();
        assertThat(note.downloadCount()).isZero();
        assertThat(note.favoriteCount()).isZero();
        assertThat(note.file()).isNotNull();
        assertThat(note.tagIds()).containsExactlyInAnyOrder(7L, 8L);
    }

    @Test
    void uploadRequiresFile() {
        assertError(() -> Note.upload(UPLOADER_ID, new CourseId(1L), new NoteTitle("标题"),
                new NoteSummary(null), new TeacherName(null), null, Set.of()), ErrorCode.SERVER_ERROR);
    }

    @Test
    void changeMetadataReplacesFieldsAndTags() {
        Note note = sampleNote();

        note.changeMetadata(UPLOADER_ID, new NoteTitle("新标题"), new NoteSummary("新简介"),
                new TeacherName("李老师"), new CourseId(2L), Set.of(9L));

        assertThat(note.title().value()).isEqualTo("新标题");
        assertThat(note.summary().value()).isEqualTo("新简介");
        assertThat(note.teacher().value()).isEqualTo("李老师");
        assertThat(note.courseId().value()).isEqualTo(2L);
        assertThat(note.tagIds()).containsExactly(9L);
    }

    @Test
    void changeMetadataRejectsNonUploader() {
        Note note = sampleNote();

        assertError(() -> note.changeMetadata(99L, new NoteTitle("新标题"), new NoteSummary(null),
                new TeacherName(null), new CourseId(2L), Set.of()), ErrorCode.FORBIDDEN);
    }

    @Test
    void changeMetadataRejectsDeletedNote() {
        Note note = sampleNote();
        note.delete(UPLOADER_ID);

        assertError(() -> note.changeMetadata(UPLOADER_ID, new NoteTitle("新标题"),
                new NoteSummary(null), new TeacherName(null), new CourseId(2L), Set.of()),
                ErrorCode.NOTE_UNAVAILABLE);
    }

    @Test
    void changeMetadataAllowsOfflineNote() {
        Note note = sampleNote();
        note.offline();

        note.changeMetadata(UPLOADER_ID, new NoteTitle("新标题"), new NoteSummary(null),
                new TeacherName(null), new CourseId(2L), Set.of());

        assertThat(note.status()).isEqualTo(NoteStatus.OFFLINE);
        assertThat(note.title().value()).isEqualTo("新标题");
    }

    @Test
    void deleteIsIdempotentAndRejectsNonUploader() {
        Note note = sampleNote();

        assertError(() -> note.delete(99L), ErrorCode.FORBIDDEN);

        note.delete(UPLOADER_ID);
        note.delete(UPLOADER_ID);

        assertThat(note.status()).isEqualTo(NoteStatus.DELETED);
        assertThat(note.isDeleted()).isTrue();
    }

    @Test
    void offlineAndRestoreAreIdempotent() {
        Note note = sampleNote();

        note.offline();
        note.offline();
        assertThat(note.status()).isEqualTo(NoteStatus.OFFLINE);

        note.restore();
        note.restore();
        assertThat(note.status()).isEqualTo(NoteStatus.ONLINE);
    }

    @Test
    void deletedNoteCannotTransition() {
        Note note = sampleNote();
        note.delete(UPLOADER_ID);

        assertError(note::offline, ErrorCode.NOTE_UNAVAILABLE);
        assertError(note::restore, ErrorCode.NOTE_UNAVAILABLE);
    }

    @Test
    void onlineNoteIsVisibleToEveryone() {
        Note note = sampleNote();

        note.ensureVisibleTo(UPLOADER_ID, false);
        note.ensureVisibleTo(99L, false);
        note.ensureVisibleTo(99L, true);
    }

    @Test
    void offlineNoteIsVisibleOnlyToUploaderAndAdmin() {
        Note note = sampleNote();
        note.offline();

        note.ensureVisibleTo(UPLOADER_ID, false);
        note.ensureVisibleTo(99L, true);
        assertError(() -> note.ensureVisibleTo(99L, false), ErrorCode.NOTE_UNAVAILABLE);
    }

    @Test
    void deletedNoteIsInvisibleToEveryone() {
        Note note = sampleNote();
        note.delete(UPLOADER_ID);

        assertError(() -> note.ensureVisibleTo(UPLOADER_ID, false), ErrorCode.NOTE_UNAVAILABLE);
        assertError(() -> note.ensureVisibleTo(99L, false), ErrorCode.NOTE_UNAVAILABLE);
        assertError(() -> note.ensureVisibleTo(99L, true), ErrorCode.NOTE_UNAVAILABLE);
    }

    @Test
    void tagIdsAreUnmodifiable() {
        Note note = sampleNote();

        assertThatThrownBy(() -> note.tagIds().add(99L))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void assignIdOnlyOnce() {
        Note note = sampleNote();

        note.assignId(100L);
        assertThat(note.id()).isEqualTo(100L);

        assertError(() -> note.assignId(101L), ErrorCode.SERVER_ERROR);
    }
}
