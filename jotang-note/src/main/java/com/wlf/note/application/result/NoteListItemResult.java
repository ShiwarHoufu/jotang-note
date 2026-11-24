package com.wlf.note.application.result;

import java.time.LocalDateTime;
import java.util.List;

/** 公开列表的一项。 */
public record NoteListItemResult(Long id,
                                 String title,
                                 CourseResult course,
                                 UploaderResult uploader,
                                 List<TagResult> tags,
                                 long viewCount,
                                 long downloadCount,
                                 long favoriteCount,
                                 LocalDateTime updatedAt,
                                 boolean isFavorited) {
}
