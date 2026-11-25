package com.wlf.note.domain.note.query;

import java.time.LocalDateTime;

/** 管理员「已下架笔记」的一行。 */
public record AdminNoteSnapshot(Long id,
                                String title,
                                Long viewCount,
                                Long downloadCount,
                                Long favoriteCount,
                                LocalDateTime createdAt,
                                LocalDateTime updatedAt,
                                Long courseId,
                                String courseName,
                                Long uploaderId) {
}
