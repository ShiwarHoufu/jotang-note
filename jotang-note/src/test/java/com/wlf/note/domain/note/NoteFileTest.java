package com.wlf.note.domain.note;

import com.wlf.note.domain.note.model.ContentType;
import com.wlf.note.domain.note.model.FileSize;
import com.wlf.note.domain.note.model.OriginalFileName;
import com.wlf.note.domain.note.model.StorageKey;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NoteFileTest {

    private static final StorageKey KEY =
            new StorageKey("notes/2026/11/123e4567-e89b-12d3-a456-426614174000.md");

    @Test
    void attachCopiesFieldsAndStartsUnpurged() {
        NoteFile file = NoteFile.attach(KEY, new OriginalFileName("笔记.md"),
                new FileSize(1024), new ContentType("text/markdown"));

        assertThat(file.storageKey()).isEqualTo(KEY);
        assertThat(file.originalName().value()).isEqualTo("笔记.md");
        assertThat(file.size().value()).isEqualTo(1024);
        assertThat(file.contentType().value()).isEqualTo("text/markdown");
        assertThat(file.isPurged()).isFalse();
    }

    @Test
    void markPurgedIsIdempotent() {
        NoteFile file = NoteFile.attach(KEY, new OriginalFileName("笔记.md"),
                new FileSize(1024), new ContentType("text/markdown"));

        file.markPurged();
        file.markPurged();

        assertThat(file.isPurged()).isTrue();
    }

    @Test
    void reconstituteRestoresPurgedFlag() {
        NoteFile file = NoteFile.reconstitute(KEY, new OriginalFileName("笔记.md"),
                new FileSize(1024), new ContentType("text/markdown"), true);

        assertThat(file.isPurged()).isTrue();
    }
}
