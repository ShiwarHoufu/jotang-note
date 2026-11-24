package com.wlf.note.infrastructure.persistence.converter;

import com.wlf.note.domain.note.Note;
import com.wlf.note.domain.note.NoteFile;
import com.wlf.note.domain.note.NoteStatus;
import com.wlf.note.domain.note.model.ContentType;
import com.wlf.note.domain.note.model.CourseId;
import com.wlf.note.domain.note.model.FileSize;
import com.wlf.note.domain.note.model.NoteSummary;
import com.wlf.note.domain.note.model.NoteTitle;
import com.wlf.note.domain.note.model.OriginalFileName;
import com.wlf.note.domain.note.model.StorageKey;
import com.wlf.note.domain.note.model.TeacherName;
import com.wlf.note.infrastructure.persistence.po.NoteFilePO;
import com.wlf.note.infrastructure.persistence.po.NotePO;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NoteConverterTest {

    @Test
    void convertsPOToDomain() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 24, 12, 0);
        NotePO po = new NotePO();
        po.setId(1L);
        po.setUploaderId(42L);
        po.setCourseId(3L);
        po.setTitle("标题");
        po.setSummary("简介");
        po.setTeacher("王老师");
        po.setStatus(NoteStatus.OFFLINE.name());
        po.setViewCount(10L);
        po.setDownloadCount(2L);
        po.setFavoriteCount(5L);
        po.setCreatedAt(now);
        po.setUpdatedAt(now);

        NoteFilePO filePo = new NoteFilePO();
        filePo.setStorageKey("notes/2026/11/123e4567-e89b-12d3-a456-426614174000.md");
        filePo.setOriginalName("笔记.md");
        filePo.setSize(1024L);
        filePo.setContentType("text/markdown");
        filePo.setIsPurged(0);

        Note note = NoteConverter.toDomain(po, filePo, List.of(7L, 8L));

        assertThat(note.id()).isEqualTo(1L);
        assertThat(note.uploaderId()).isEqualTo(42L);
        assertThat(note.courseId().value()).isEqualTo(3L);
        assertThat(note.title().value()).isEqualTo("标题");
        assertThat(note.summary().value()).isEqualTo("简介");
        assertThat(note.teacher().value()).isEqualTo("王老师");
        assertThat(note.status()).isEqualTo(NoteStatus.OFFLINE);
        assertThat(note.viewCount()).isEqualTo(10L);
        assertThat(note.downloadCount()).isEqualTo(2L);
        assertThat(note.favoriteCount()).isEqualTo(5L);
        assertThat(note.createdAt()).isEqualTo(now);
        assertThat(note.updatedAt()).isEqualTo(now);
        assertThat(note.file().originalName().value()).isEqualTo("笔记.md");
        assertThat(note.tagIds()).containsExactlyInAnyOrder(7L, 8L);
    }

    @Test
    void convertsDomainToPO() {
        Note note = Note.reconstitute(1L, 42L,
                new CourseId(3L),
                new NoteTitle("标题"),
                new NoteSummary("简介"),
                new TeacherName("王老师"),
                NoteStatus.ONLINE, 10L, 2L, 5L,
                LocalDateTime.of(2026, 11, 24, 12, 0),
                LocalDateTime.of(2026, 11, 24, 13, 0),
                null,
                NoteFile.attach(
                        new StorageKey("notes/2026/11/123e4567-e89b-12d3-a456-426614174000.md"),
                        new OriginalFileName("笔记.md"),
                        new FileSize(1024),
                        new ContentType("text/markdown")),
                List.of(7L));

        NotePO po = NoteConverter.toPO(note);

        assertThat(po.getId()).isEqualTo(1L);
        assertThat(po.getUploaderId()).isEqualTo(42L);
        assertThat(po.getCourseId()).isEqualTo(3L);
        assertThat(po.getTitle()).isEqualTo("标题");
        assertThat(po.getSummary()).isEqualTo("简介");
        assertThat(po.getTeacher()).isEqualTo("王老师");
        assertThat(po.getStatus()).isEqualTo(NoteStatus.ONLINE.name());
        assertThat(po.getViewCount()).isEqualTo(10L);
        assertThat(po.getDownloadCount()).isEqualTo(2L);
        assertThat(po.getFavoriteCount()).isEqualTo(5L);
    }
}
