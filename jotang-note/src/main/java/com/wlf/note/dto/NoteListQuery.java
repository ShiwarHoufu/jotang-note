package com.wlf.note.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 列表 / 搜索的查询参数。
 */
@Getter
@Setter
public class NoteListQuery {

    /** 课程筛选。null / 不传表示不筛 */
    private Long courseId;

    /** 标签筛选。null / 不传表示不筛。与 {@code courseId} 同时给就是「与」关系 */
    private Long tagId;

    /**
     * 标题关键词。null / 空白表示不搜。与 {@code courseId} / {@code tagId} 同时给就是「与」关系。
     *
     * <p>上限 50 是给输入框兜底的，不是匹配能力的边界。校验在 trim 之前，所以纯粹的空格串
     * 能过这一关，随后在 {@link com.wlf.note.NoteService#list} 里按「不搜」处理。
     */
    @Size(max = 50, message = "搜索词最多 50 个字符")
    private String keyword;

    /** 排序方式。不传时按最近更新。取值非法会在绑定阶段就被拦下，不会流到 SQL */
    private Sort sort = Sort.LATEST;

    @Min(value = 1, message = "页码从 1 开始")
    private int page = 1;

    @Min(value = 1, message = "每页至少 1 条")
    @Max(value = 50, message = "每页最多 50 条")
    private int size = 20;

    /**
     * 排序方式。
     */
    public enum Sort {

        /** 最近更新：按 {@code updated_at} 倒序，即「用户最后编辑笔记的时间」（§3.3） */
        LATEST,

        /** 热门·浏览 */
        HOT_VIEW,

        /** 热门·下载 */
        HOT_DOWNLOAD
    }
}
