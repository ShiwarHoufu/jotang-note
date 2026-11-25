package com.wlf.note.domain.favorite;

import com.wlf.note.domain.favorite.query.FavoriteItemSnapshot;
import com.wlf.note.domain.shared.PageSlice;

import java.util.Collection;
import java.util.Set;

/** 收藏读侧。 */
public interface FavoriteQueryRepository {

    /** 我的收藏，只包含笔记状态为 ONLINE 的项。 */
    PageSlice<FavoriteItemSnapshot> findPage(Long userId, int page, int size);

    Set<Long> findFavoritedNoteIds(Long userId, Collection<Long> noteIds);
}
