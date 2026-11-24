package com.wlf.note.interfaces.web.dto;

import com.wlf.note.domain.note.NoteStatus;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 「我的上传」的一项出参。
 *
 * <p><b>不返回上传者（{@code nickname} / {@code avatarUrl} / {@code collegeName}）。</b>
 * 本列表里上传者恒等于当前登录用户
 *
 * <p>不返回 {@code summary} / {@code teacher} / {@code file}，同公开列表不做预览。
 */
public record MyNoteItemResponse(
        Long id,
        NoteStatus status,
        String title,
        CourseResponse course,
        List<TagResponse> tags,
        long viewCount,
        long downloadCount,
        long favoriteCount,
        LocalDateTime createdAt,
        boolean isFavorited
) {
}
