package com.wlf.note.application.port.out;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.wlf.note.application.favorite.dto.FavoriteItemRow;
import com.wlf.note.domain.favorite.Favorite;

import java.util.Collection;
import java.util.Set;

/**
 * 收藏数据的出站端口。
 */
public interface FavoriteDataAccess {

    void insert(Favorite favorite);

    int deleteByUserAndNote(Long userId, Long noteId);

    IPage<FavoriteItemRow> selectFavoritePage(IPage<FavoriteItemRow> page, Long userId);

    Set<Long> findFavoritedNoteIds(Long userId, Collection<Long> noteIds);
}
