package com.wlf.note.domain.note.query;

import java.time.LocalDateTime;

/** 公开列表的一行。 */
public record NoteListSnapshot(Long id,
                               String title,
                               Long viewCount,
                               Long downloadCount,
                               Long favoriteCount,
                               LocalDateTime updatedAt,
                               Long courseId,
                               String courseName,
                               Long uploaderId) {
}
