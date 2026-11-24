package com.wlf.note.interfaces.web.dto;

import com.wlf.note.domain.note.NoteStatus;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 收藏项出参：正常项，或「已下架 / 已删除」占位。
 */
public record FavoriteItemResponse(
        Long noteId,
        NoteStatus status,
        LocalDateTime favoritedAt,
        String title,
        CourseResponse course,
        UploaderResponse uploader,
        List<TagResponse> tags,
        Long viewCount,
        Long downloadCount,
        Long favoriteCount,
        LocalDateTime updatedAt
) {
}
