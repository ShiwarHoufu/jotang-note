package com.wlf.note.dto;

import com.wlf.catalog.dto.CourseResponse;
import com.wlf.catalog.dto.TagResponse;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 列表 / 搜索项出参。
 *
 * <p>没有的东西：
 * <ul>
 *   <li>{@code status}：本接口只查 {@code ONLINE}</li>
 *   <li>{@code summary} / {@code teacher}：卡片不展示</li>
 *   <li>{@code createdAt}：卡片显示的是最后更新于</li>
 *   <li>{@code file}：列表不做预览，也不显示文件类型与大小</li>
 * </ul>
 *
 * <p>{@code updatedAt} 的语义是用户最后编辑笔记的时间</b>，不是这一行最后被写的时间。
 *
 * @param tags 没有标签时是空数组而不是 null，前端可以无条件遍历。
 * @param isFavorited 当前登录者是否收藏过。
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
