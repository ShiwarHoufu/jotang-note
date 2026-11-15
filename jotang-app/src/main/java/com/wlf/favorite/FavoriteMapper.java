package com.wlf.favorite;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.wlf.entity.Favorite;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * favorite 表的数据访问。
 *
 * <p>单表 CRUD 由 {@link BaseMapper} 提供——详情页的「是否已收藏」走
 * {@code uk_user_note} 唯一索引的等值查询（{@link FavoriteService#isFavorited}）；
 */
@Mapper
public interface FavoriteMapper extends BaseMapper<Favorite> {

    /**
     * 某个用户的收藏，一页。
     *
     * <p>不筛 {@code note.status}：三种状态都要取回来交给 Service 渲染占位；
     * 这个列表只有本人能看，所以只需要 {@code userId}。
     *
     * @return 一页数据，按收藏时间倒序；{@code total} 由插件回填到返回的 {@code IPage} 上
     */
    IPage<FavoriteItemRow> selectFavoritePage(IPage<FavoriteItemRow> page, @Param("userId") Long userId);
}
