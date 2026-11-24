package com.wlf.note.infrastructure.persistence.converter;

import com.wlf.note.domain.note.NoteFile;
import com.wlf.note.domain.note.model.ContentType;
import com.wlf.note.domain.note.model.FileSize;
import com.wlf.note.domain.note.model.OriginalFileName;
import com.wlf.note.domain.note.model.StorageKey;
import com.wlf.note.infrastructure.persistence.po.NoteFilePO;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NoteFileConverterTest {

    private static final String KEY = "notes/2026/11/123e4567-e89b-12d3-a456-426614174000.md";

    @Test
    void convertsPOToDomain() {
        NoteFilePO po = new NoteFilePO();
        po.setStorageKey(KEY);
        po.setOriginalName("笔记.md");
        po.setSize(1024L);
        po.setContentType("text/markdown");
        po.setIsPurged(1);

        NoteFile file = NoteFileConverter.toDomain(po);

        assertThat(file.storageKey().value()).isEqualTo(KEY);
        assertThat(file.originalName().value()).isEqualTo("笔记.md");
        assertThat(file.size().value()).isEqualTo(1024L);
        assertThat(file.contentType().value()).isEqualTo("text/markdown");
        assertThat(file.isPurged()).isTrue();
    }

    @Test
    void convertsDomainToPO() {
        NoteFile file = NoteFile.attach(new StorageKey(KEY),
                new OriginalFileName("笔记.md"),
                new FileSize(1024),
                new ContentType("text/markdown"));

        NoteFilePO po = NoteFileConverter.toPO(file, 88L);

        assertThat(po.getNoteId()).isEqualTo(88L);
        assertThat(po.getStorageKey()).isEqualTo(KEY);
        assertThat(po.getOriginalName()).isEqualTo("笔记.md");
        assertThat(po.getSize()).isEqualTo(1024L);
        assertThat(po.getContentType()).isEqualTo("text/markdown");
        assertThat(po.getIsPurged()).isZero();
    }
}
