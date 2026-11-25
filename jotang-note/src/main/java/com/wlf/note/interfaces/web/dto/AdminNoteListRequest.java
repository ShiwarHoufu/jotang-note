package com.wlf.note.interfaces.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** 管理员「已下架笔记」列表的查询参数。 */
@Getter
@Setter
public class AdminNoteListRequest {

    /** 标题关键词。null / 空白表示不筛。 */
    @Size(max = 50, message = "搜索词最多 50 个字符")
    private String keyword;

    @Min(value = 1, message = "页码从 1 开始")
    private int page = 1;

    @Min(value = 1, message = "每页至少 1 条")
    @Max(value = 50, message = "每页最多 50 条")
    private int size = 20;
}
