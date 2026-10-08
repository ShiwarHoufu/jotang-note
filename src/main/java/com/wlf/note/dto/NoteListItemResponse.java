package com.wlf.note.dto;

import com.wlf.catalog.dto.CourseResponse;
import com.wlf.catalog.dto.TagResponse;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 列表 / 搜索项出参。见《概要设计》§5.4。
 *
 * <p>列表项是详情项的<b>真子集</b>：凡是两边都给的字段（{@code course}、{@code uploader}、
 * {@code tags}、三个计数、{@code isFavorited}）形状与含义完全一致，由
 * {@link UploaderResponse} 这类共用类型保证。
 *
 * <p><b>刻意没有的东西，以及各自的理由</b>：
 *
 * <ul>
 *   <li>{@code status}：本接口只查 {@code ONLINE}（§4.1：列表/搜索对 OFFLINE、DELETED 不可见），
 *       这个字段恒等于一个值。详情页要它是因为那里它是变量</li>
 *   <li>{@code summary} / {@code teacher}：卡片不展示</li>
 *   <li>{@code createdAt}：卡片显示的是「最后更新于」</li>
 *   <li>{@code file}：列表不做预览，也不显示文件类型与大小</li>
 * </ul>
 *
 * <p>{@code updatedAt} 的语义是<b>用户最后编辑笔记的时间</b>（§3.3），不是「这一行最后被写的时间」——
 * 后者会被浏览量自增污染，那样热度越高的笔记时间越新。它同时也是 {@code sort=latest} 的排序键。
 *
 * <p>列表里带 {@code isFavorited}，意味着这个接口<b>必须知道「谁在看」</b>，
 * {@code userId} 要从 JWT 取而不是请求参数。它由一次批量 {@code IN} 查询装配，
 * 不是逐条查——详见 {@code NoteService#list}。
 *
 * @param tags 没有标签时是<b>空数组而不是 null</b>，前端可以无条件遍历。单篇上限 10 个（{@code TagService}
 *             的护栏），全量返回由前端决定展示几个——服务端截断是有损的
 * @param isFavorited 当前登录者是否收藏过。<b>与详情同一个口径</b>：如实反映
 *             {@code favorite} 表里的关系，不做「非 ONLINE 一律 false」那种为配合渲染而做的加工
 *             （§6.5 不解除收藏关系）。本接口只出 ONLINE 笔记，所以这一层不会遇到那个岔口，
 *             但两处的语义必须一致，否则同一篇笔记在列表和详情上会显示成不同的收藏态
 */
public record NoteListItemResponse(
        Long id,
        String title,
        CourseResponse course,
        UploaderResponse uploader,
        List<TagResponse> tags,
        long viewCount,
        long downloadCount,
        long favoriteCount,
        LocalDateTime updatedAt,
        boolean isFavorited
) {
}
