package com.wlf.note.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.wlf.note.domain.favorite.query.FavoriteItemSnapshot;
import com.wlf.note.infrastructure.persistence.po.FavoritePO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * favorite 表的数据访问。
 *
 * <p>单表 CRUD 由 {@link BaseMapper} 提供。
 */
@Mapper
public interface FavoriteMapper extends BaseMapper<FavoritePO> {

    /**
     * 某个用户的收藏，一页。
     *
     * <p>不筛 {@code note.status}：三种状态都要取回来交给 Service 渲染占位；
     * 这个列表只有本人能看，所以只需要 {@code userId}。
     */
    IPage<FavoriteItemSnapshot> selectFavoritePage(IPage<FavoriteItemSnapshot> page,
                                                   @Param("userId") Long userId);
}
