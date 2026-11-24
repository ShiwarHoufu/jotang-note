package com.wlf.note.domain.favorite.query;

import java.time.LocalDateTime;

/** 「我的收藏」一页的一行。 */
public record FavoriteItemSnapshot(Long noteId,
                                   LocalDateTime favoritedAt,
                                   String status,
                                   String title,
                                   Long courseId,
                                   String courseName,
                                   Long uploaderId,
                                   Long viewCount,
                                   Long downloadCount,
                                   Long favoriteCount,
                                   LocalDateTime updatedAt) {
}
