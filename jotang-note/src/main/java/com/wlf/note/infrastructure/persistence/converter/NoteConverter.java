package com.wlf.note.infrastructure.persistence.converter;

import com.wlf.note.domain.note.Note;
import com.wlf.note.domain.note.NoteStatus;
import com.wlf.note.domain.note.model.CourseId;
import com.wlf.note.domain.note.model.NoteSummary;
import com.wlf.note.domain.note.model.NoteTitle;
import com.wlf.note.domain.note.model.TeacherName;
import com.wlf.note.infrastructure.persistence.po.NoteFilePO;
import com.wlf.note.infrastructure.persistence.po.NotePO;

import java.util.Collection;

/** Note ↔ note 行。附件与标签由仓储单独装配。 */
public final class NoteConverter {

    private NoteConverter() {
    }

    public static Note toDomain(NotePO po, NoteFilePO filePo, Collection<Long> tagIds) {
        return Note.reconstitute(
                po.getId(),
                po.getUploaderId(),
                new CourseId(po.getCourseId()),
                new NoteTitle(po.getTitle()),
                new NoteSummary(po.getSummary()),
                new TeacherName(po.getTeacher()),
                NoteStatus.fromName(po.getStatus()),
                valueOrZero(po.getViewCount()),
                valueOrZero(po.getDownloadCount()),
                valueOrZero(po.getFavoriteCount()),
                po.getCreatedAt(),
                po.getUpdatedAt(),
                po.getDeletedAt(),
                NoteFileConverter.toDomain(filePo),
                tagIds);
    }

    public static NotePO toPO(Note note) {
        NotePO po = new NotePO();
        po.setId(note.id());
        po.setUploaderId(note.uploaderId());
        po.setCourseId(note.courseId().value());
        po.setTitle(note.title().value());
        po.setSummary(note.summary().value());
        po.setTeacher(note.teacher().value());
        po.setStatus(note.status().name());
        po.setViewCount(note.viewCount());
        po.setDownloadCount(note.downloadCount());
        po.setFavoriteCount(note.favoriteCount());
        po.setCreatedAt(note.createdAt());
        po.setUpdatedAt(note.updatedAt());
        po.setDeletedAt(note.deletedAt());
        return po;
    }

    private static long valueOrZero(Long value) {
        return value == null ? 0L : value;
    }
}
