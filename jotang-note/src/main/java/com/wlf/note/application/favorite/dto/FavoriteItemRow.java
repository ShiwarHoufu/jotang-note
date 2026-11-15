package com.wlf.note.application.favorite.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 「我的收藏」主干查询的一行，{@link FavoriteMapper#selectFavoritePage} 的返回类型。
 */
@Getter
@Setter
public class FavoriteItemRow {


    private Long noteId;
    private LocalDateTime favoritedAt;
    /** 由 Service 决定正常项还是占位项 */
    private String status;
    private String title;

    // ---- course ----
    private Long courseId;
    private String courseName;

    // ---- user（上传者）----
    private Long uploaderId;

    // ---- 计数与编辑时间 ----
    private Long viewCount;
    private Long downloadCount;
    private Long favoriteCount;

    private LocalDateTime updatedAt;
}
