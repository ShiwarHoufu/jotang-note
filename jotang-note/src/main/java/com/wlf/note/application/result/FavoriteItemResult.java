package com.wlf.note.application.result;

import com.wlf.note.domain.note.NoteStatus;

import java.time.LocalDateTime;
import java.util.List;

/** 收藏项，正常项或占位项。 */
public record FavoriteItemResult(Long noteId,
                                 NoteStatus status,
                                 LocalDateTime favoritedAt,
                                 String title,
                                 CourseResult course,
                                 UploaderResult uploader,
                                 List<TagResult> tags,
                                 Long viewCount,
                                 Long downloadCount,
                                 Long favoriteCount,
                                 LocalDateTime updatedAt) {
}
