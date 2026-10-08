package com.wlf.favorite;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.wlf.entity.Favorite;
import org.springframework.stereotype.Service;

/**
 * 收藏业务：仅 ONLINE 可收藏；列表按 note.status 渲染「已下架/已删除」占位。
 * 见《概要设计》§6.5。
 *
 * <p>目前只落地了读取侧的一个方法（详情页要它填 {@code isFavorited}）；
 * 收藏 / 取消收藏 / 我的收藏随后续切片补上。
 */
@Service
public class FavoriteService {

    private final FavoriteMapper favoriteMapper;

    public FavoriteService(FavoriteMapper favoriteMapper) {
        this.favoriteMapper = favoriteMapper;
    }

    /**
     * 某个用户是否收藏了某篇笔记。详情页用它填 {@code isFavorited}（§5.4）。
     *
     * <p><b>只看 favorite 表，不看笔记状态。</b>§6.5 明确「占位项不可预览/下载，
     * 但不解除收藏关系」，所以已下架 / 已删除的笔记只要关系还在就返回 true。
     * 这个字段回答的是「这条关系存在吗」，而不是「现在还能收藏吗」——
     * 后者由 {@code note.status} 单独回答，前端据此隐藏按钮（§4.1）。
     * 若在这里顺手改成「非 ONLINE 一律 false」，就等于让数据去配合渲染，
     * 而关系实际还在，等笔记恢复上架时这个字段又会自己变回 true。
     *
     * <p>用 {@code exists} 而非 {@code selectCount}：存在性判断不必知道有几行，
     * 而 {@code uk_user_note} 保证至多一行。查询走该唯一索引的等值匹配。
     *
     * @param userId 当前登录者，取自 JWT
     * @param noteId 任意笔记 id，<b>允许是不存在的 id</b>——「笔记不存在」与
     *               「笔记存在但没收藏」对调用方的含义相同，都是 false。
     *               笔记存在与否由详情接口自己判定（它要为此报 40400），
     *               本方法只回答收藏关系这一个问题，不越界去判另一件事
     * @return null 参数视为未收藏：调用方是「JWT 里的 userId」与「路径上的 noteId」，
     *         两者都不该为 null，真出现了也不值得让请求失败，返回 false 即可
     */
    public boolean isFavorited(Long userId, Long noteId) {
        if (userId == null || noteId == null) {
            return false;
        }
        return favoriteMapper.exists(Wrappers.<Favorite>lambdaQuery()
                .eq(Favorite::getUserId, userId)
                .eq(Favorite::getNoteId, noteId));
    }
}
