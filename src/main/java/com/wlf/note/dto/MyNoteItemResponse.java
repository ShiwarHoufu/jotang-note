package com.wlf.note.dto;

import com.wlf.catalog.dto.CourseResponse;
import com.wlf.catalog.dto.TagResponse;
import com.wlf.note.NoteStatus;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 「我的上传」的一项出参。
 *
 * <p><b>为什么不复用 {@link NoteListItemResponse}</b>：那里<b>刻意没有 {@code status}</b>
 * （公开列表只查 {@code ONLINE}，那个字段恒等于一个值，所以不给）。而本列表要返回
 * {@code ONLINE} 与 {@code OFFLINE} 两种，{@code status} 是变量，前端靠它标注「已下架」。
 * 一个恒为常量、一个是变量，这正是不复用的理由——与 {@code FavoriteItemResponse} 单开同理。
 *
 * <p><b>不返回上传者（{@code nickname} / {@code avatarUrl} / {@code collegeName}）。</b>
 * 本列表里上传者恒等于当前登录用户，三项全是不变的常量。这与
 * {@code NoteListItemResponse} 省略 {@code status} 是同一个口径：<b>恒等于一个值的字段不给</b>。
 * 顺带的好处是这条查询连 {@code user} / {@code college} 都不用 JOIN，比公开列表还便宜。
 *
 * <p><b>不返回 {@code summary} / {@code teacher} / {@code file}</b>：同公开列表，
 * 卡片不展示这些（列表不做预览）。
 *
 * @param status     {@link NoteStatus} 枚举而非裸字符串。<b>只会是 {@code ONLINE} 或
 *                   {@code OFFLINE}</b>
 * @param createdAt  上传时刻，同时是本列表的排序键。
 * @param isFavorited 当前登录者是否收藏了这一篇。
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
