package com.wlf.favorite;

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

    // ---- user（上传者）= ----
    private Long uploaderId;
    private String nickname;

    /** 头像的 OSS 对象键，不是 URL；由 Service 拼成公网地址 */
    private String avatar;

    // ---- college（上传者的学院，D8 推导而来）----
    private String collegeName;

    // ---- 计数与编辑时间 ----
    private Long viewCount;
    private Long downloadCount;
    private Long favoriteCount;

    private LocalDateTime updatedAt;
}
