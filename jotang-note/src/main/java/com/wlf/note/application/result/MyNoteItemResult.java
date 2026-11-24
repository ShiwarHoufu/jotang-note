package com.wlf.note.application.result;

import com.wlf.note.domain.note.NoteStatus;

import java.time.LocalDateTime;
import java.util.List;

/** 「我的上传」的一项。 */
public record MyNoteItemResult(Long id,
                               NoteStatus status,
                               String title,
                               CourseResult course,
                               List<TagResult> tags,
                               long viewCount,
                               long downloadCount,
                               long favoriteCount,
                               LocalDateTime createdAt,
                               boolean isFavorited) {
}
