package com.wlf.favorite;

import org.apache.ibatis.annotations.Mapper;

/**
 * favorite 表的数据访问。
 * 收藏列表 JOIN note 渲染占位，走 FavoriteMapper.xml。
 */
@Mapper
public interface FavoriteMapper {
}
