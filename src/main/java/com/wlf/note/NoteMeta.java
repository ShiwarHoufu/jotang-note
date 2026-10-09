package com.wlf.note;

import com.wlf.catalog.dto.TagResponse;

import java.util.List;

/**
 * 笔记详情的缓存载荷：把「七表 join 出来的主干」与「标签」装成一个整体，共用一个 key。
 *
 * <p>缓存的<b>边界就画在「数据库查回来的东西」这一层</b>：{@link NoteDetailRow} 是
 * {@code selectDetail} 的原始投影，标签是 {@code selectTagRefs} 的结果。响应体那层
 * 「策略」（非 ONLINE 置空 file、{@code previewMode} 现推、头像键转 URL、{@link NoteStatus}
 * 转枚举）不进缓存，每次在 {@link NoteService#detail} 里现做——它们是纯内存计算，
 * 且正是 {@link NoteDetailRow} 注释里说的、应当留在 Service 的可读可测的那部分。
 *
 * <p><b>为什么不是缓存整个 {@code NoteDetailResponse}</b>：
 * 那个记录里有一个{@code isFavorited} 字段依赖浏览者。缓存的 key 只有 noteId，一旦把它也存进去，
 * 用户 A 的收藏状态就会被回给用户 B——那不是「缓存陈旧」，是串号。剔掉它再缓存整个响应，
 * 又等于再造一个与响应几乎同形、将来加字段要改两处的类型，得不偿失。
 */
public record NoteMeta(NoteDetailRow row, List<TagResponse> tags) {
}
