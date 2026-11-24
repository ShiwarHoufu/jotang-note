package com.wlf.note.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.wlf.note.domain.favorite.Favorite;
import com.wlf.note.domain.favorite.FavoriteRepository;
import com.wlf.note.infrastructure.persistence.FavoriteMapper;
import com.wlf.note.infrastructure.persistence.converter.FavoriteConverter;
import com.wlf.note.infrastructure.persistence.po.FavoritePO;
import org.springframework.stereotype.Repository;

/** 收藏写侧仓储。 */
@Repository
public class FavoriteRepositoryImpl implements FavoriteRepository {

    private final FavoriteMapper favoriteMapper;

    public FavoriteRepositoryImpl(FavoriteMapper favoriteMapper) {
        this.favoriteMapper = favoriteMapper;
    }

    @Override
    public void insert(Favorite favorite) {
        FavoritePO po = FavoriteConverter.toPO(favorite);
        favoriteMapper.insert(po);
        favorite.assignId(po.getId());
    }

    @Override
    public int deleteByUserAndNote(Long userId, Long noteId) {
        return favoriteMapper.delete(Wrappers.<FavoritePO>lambdaQuery()
                .eq(FavoritePO::getUserId, userId)
                .eq(FavoritePO::getNoteId, noteId));
    }
}
