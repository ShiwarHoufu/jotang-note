package com.wlf.favorite;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wlf.entity.Favorite;
import org.apache.ibatis.annotations.Mapper;

/**
 * favorite 表的数据访问。
 *
 * <p>单表 CRUD 由 {@link BaseMapper} 提供——详情页的「是否已收藏」就落在它上面
 * （{@link FavoriteService#isFavorited}），走 {@code uk_user_note} 唯一索引的等值查询。
 *
 * <p>继承 {@code BaseMapper} 是在补上「我的收藏」之前就要做的选择：那条查询是
 * {@code favorite JOIN note}——既要用 {@code orderBy} 排收藏时间，又要读 {@code note.status}
 * 决定渲染正常项还是占位项（§6.5），跨表检索走 {@code FavoriteMapper.xml}。
 * 与 {@code NoteMapper} 是同一套分工：<b>单表用 BaseMapper，跨表检索用 XML</b>。
 */
@Mapper
public interface FavoriteMapper extends BaseMapper<Favorite> {
}
