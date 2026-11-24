package com.wlf.note.domain.note.query;

import com.wlf.note.domain.note.NoteStatus;

import java.time.LocalDateTime;

/** 详情投影：note / note_file / course 一次连接出的行。 */
public record NoteDetailSnapshot(Long id,
                                 String title,
                                 String summary,
                                 String teacher,
                                 String status,
                                 Long viewCount,
                                 Long downloadCount,
                                 Long favoriteCount,
                                 LocalDateTime createdAt,
                                 LocalDateTime updatedAt,
                                 String originalName,
                                 Long size,
                                 String contentType,
                                 Long courseId,
                                 String courseName,
                                 Long uploaderId) {

    public NoteStatus noteStatus() {
        return NoteStatus.fromName(status);
    }
}
