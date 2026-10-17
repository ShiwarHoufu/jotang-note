package com.wlf.favorite.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

/**
 * 我的收藏 查询参数。
 */
@Getter
@Setter
public class FavoriteListQuery {

    @Min(value = 1, message = "页码从 1 开始")
    private int page = 1;

    @Min(value = 1, message = "每页至少 1 条")
    @Max(value = 50, message = "每页最多 50 条")
    private int size = 20;
}
