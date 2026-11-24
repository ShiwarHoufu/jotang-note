package com.wlf.note.domain.note.query;

import com.wlf.note.domain.note.NoteStatus;

import java.time.LocalDateTime;

/** 「我的上传」的一行。 */
public record MyNoteSnapshot(Long id,
                             String status,
                             String title,
                             Long viewCount,
                             Long downloadCount,
                             Long favoriteCount,
                             LocalDateTime createdAt,
                             Long courseId,
                             String courseName) {

    public NoteStatus noteStatus() {
        return NoteStatus.fromName(status);
    }
}
