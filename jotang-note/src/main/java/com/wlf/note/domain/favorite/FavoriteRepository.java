package com.wlf.note.domain.favorite;

/** 收藏写侧。 */
public interface FavoriteRepository {

    void insert(Favorite favorite);

    /** @return 删除的行数 */
    int deleteByUserAndNote(Long userId, Long noteId);
}
