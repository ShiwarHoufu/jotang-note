package com.wlf.note.dto;

import com.wlf.catalog.dto.CourseResponse;
import com.wlf.catalog.dto.TagResponse;
import com.wlf.note.NoteStatus;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 「我的上传」的一项出参。见《概要设计》§5.4、§4.1。
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
 * <p><b>不返回 {@code isFavorited}</b>：这是一个管理自己笔记的列表，卡片上的动作是
 * 「编辑 / 删除」，收藏自己的笔记语义上说不通；不给也省掉一次 {@code favorite} 的批量查询。
 * 公开列表与详情页带它，是因为那里看的是别人的笔记。
 *
 * <p><b>不返回 {@code summary} / {@code teacher} / {@code file}</b>：同公开列表，
 * 卡片不展示这些（列表不做预览）。
 *
 * <p><b>只给 {@code createdAt}，不给 {@code updatedAt}</b>：排序键是上传时间，
 * 卡片上要显示的自然也是「上传于」。这与 {@code CourseResponse} 不加 {@code isOther} 的取舍一致——
 * 响应体多一个字段是非破坏性变更，而加上再拿掉是破坏性的，所以宁可从窄。
 *
 * @param status     {@link NoteStatus} 枚举而非裸字符串。<b>只会是 {@code ONLINE} 或
 *                   {@code OFFLINE}</b>——{@code DELETED} 被 SQL 无条件排除（§4.1 的表里
 *                   「我的上传」这一列对 {@code DELETED} 是「不可见」）。前端据此给
 *                   {@code OFFLINE} 的卡片加「已下架」角标
 * @param tags       没有标签时是<b>空数组而不是 null</b>，前端可以无条件遍历
 * @param createdAt  上传时刻，同时是本列表的排序键。与 {@code updatedAt}（用户最后编辑笔记的时间，
 *                   §3.3）是两回事——编辑过的笔记这两个值会差很远
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
        LocalDateTime createdAt
) {
}
