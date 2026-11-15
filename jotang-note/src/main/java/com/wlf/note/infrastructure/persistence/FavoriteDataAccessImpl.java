package com.wlf.note.infrastructure.persistence;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.wlf.note.application.favorite.dto.FavoriteItemRow;
import com.wlf.note.application.port.out.FavoriteDataAccess;
import com.wlf.note.domain.favorite.Favorite;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 收藏数据端口的 MyBatis 实现。
 */
@Repository
public class FavoriteDataAccessImpl implements FavoriteDataAccess {

    private final FavoriteMapper favoriteMapper;

    public FavoriteDataAccessImpl(FavoriteMapper favoriteMapper) {
        this.favoriteMapper = favoriteMapper;
    }

    @Override
    public void insert(Favorite favorite) {
        favoriteMapper.insert(favorite);
    }

    @Override
    public int deleteByUserAndNote(Long userId, Long noteId) {
        return favoriteMapper.delete(Wrappers.<Favorite>lambdaQuery()
                .eq(Favorite::getUserId, userId)
                .eq(Favorite::getNoteId, noteId));
    }

    @Override
    public IPage<FavoriteItemRow> selectFavoritePage(IPage<FavoriteItemRow> page, Long userId) {
        return favoriteMapper.selectFavoritePage(page, userId);
    }

    @Override
    public Set<Long> findFavoritedNoteIds(Long userId, Collection<Long> noteIds) {
        return favoriteMapper.selectList(Wrappers.<Favorite>lambdaQuery()
                        .select(Favorite::getNoteId)
                        .eq(Favorite::getUserId, userId)
                        .in(Favorite::getNoteId, noteIds))
                .stream()
                .map(Favorite::getNoteId)
                .collect(Collectors.toSet());
    }
}
