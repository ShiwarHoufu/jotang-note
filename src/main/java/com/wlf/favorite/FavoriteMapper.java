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
 * 收藏 / 取消收藏也只是在这个表上插一行、删一行，同样用它。
 *
 * <p>跨表检索走 {@code FavoriteMapper.xml}：那条查询是 {@code favorite JOIN note}，
 * 既要读 {@code note.status} 决定渲染正常项还是占位项（§6.5），又要按收藏时间排序。
 * 与 {@code NoteMapper} 是同一套分工：<b>单表用 BaseMapper，跨表检索用 XML</b>。
 *
 * <p>注意本 Mapper <b>不含 {@code note} 表的写语句</b>：{@code favorite_count} 的增减在
 * {@link com.wlf.note.NoteMapper} 上，理由见该接口的注释（那是 §1.1 允许的
 * 「跨模块写对方单表列」，与读路径的 JOIN 是两件事）。
 */
@Mapper
public interface FavoriteMapper extends BaseMapper<Favorite> {

    /**
     * 某个用户的收藏，一页。见 §5.5、§6.5。
     *
     * <p>第一个参数必须是 {@code IPage}：分页插件靠它识别出这是分页查询，并自动改写出一条
     * count 语句（{@code optimizeCountSql} 会顺手去掉 {@code ORDER BY}）。条数不由调用方传，
     * 由 {@code page} 对象携带。
     *
     * <p><b>不筛 {@code note.status}</b>：三种状态都要取回来交给 Service 渲染占位
     * （§6.5「不解除收藏关系」），再筛一次就等于把占位项做没了。
     *
     * <p>入参是「谁在看」而不是「看谁」：这个列表只有本人能看，所以只需要 {@code userId}。
     * 用户 id 取自 JWT，绝不能由请求参数传入——否则任何人都能翻别人的收藏。
     *
     * @return 一页数据，按收藏时间倒序；{@code total} 由插件回填到返回的 {@code IPage} 上
     */
    IPage<FavoriteItemRow> selectFavoritePage(IPage<FavoriteItemRow> page, @Param("userId") Long userId);
}
