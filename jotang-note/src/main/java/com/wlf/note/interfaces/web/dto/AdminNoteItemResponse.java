package com.wlf.note.interfaces.web.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理员「已下架笔记」列表的一项。
 *
 * <p>不返回收藏状态：列表用于定位待处理笔记，全文看详情。
 */
public record AdminNoteItemResponse(
        Long id,
        String title,
        CourseResponse course,
        UploaderResponse uploader,
        List<TagResponse> tags,
        long viewCount,
        long downloadCount,
        long favoriteCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
