package com.wlf.note.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wlf.note.domain.favorite.FavoriteQueryRepository;
import com.wlf.note.domain.favorite.query.FavoriteItemSnapshot;
import com.wlf.note.domain.shared.PageSlice;
import com.wlf.note.infrastructure.persistence.FavoriteMapper;
import com.wlf.note.infrastructure.persistence.po.FavoritePO;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

/** 收藏读侧仓储。 */
@Repository
public class FavoriteQueryRepositoryImpl implements FavoriteQueryRepository {

    private final FavoriteMapper favoriteMapper;

    public FavoriteQueryRepositoryImpl(FavoriteMapper favoriteMapper) {
        this.favoriteMapper = favoriteMapper;
    }

    @Override
    public PageSlice<FavoriteItemSnapshot> findPage(Long userId, int page, int size) {
        IPage<FavoriteItemSnapshot> result = favoriteMapper.selectFavoritePage(
                new Page<>(page, size), userId);
        return new PageSlice<>(result.getRecords(), result.getTotal());
    }

    @Override
    public Set<Long> findFavoritedNoteIds(Long userId, Collection<Long> noteIds) {
        if (userId == null || noteIds == null || noteIds.isEmpty()) {
            return Set.of();
        }
        return favoriteMapper.selectList(Wrappers.<FavoritePO>lambdaQuery()
                        .select(FavoritePO::getNoteId)
                        .eq(FavoritePO::getUserId, userId)
                        .in(FavoritePO::getNoteId, noteIds))
                .stream()
                .map(FavoritePO::getNoteId)
                .collect(Collectors.toSet());
    }
}
