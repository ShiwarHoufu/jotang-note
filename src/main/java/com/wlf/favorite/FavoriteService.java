package com.wlf.favorite;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.wlf.entity.Favorite;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 收藏业务：仅 ONLINE 可收藏；列表按 note.status 渲染「已下架/已删除」占位。
 * 见《概要设计》§6.5。
 *
 * <p>目前只落地了读取侧的两个方法（详情与列表页要它们填 {@code isFavorited}）；
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
     * <p>实现上委托给 {@link #favoritedNoteIds}，而不是另写一条 {@code exists}：
     * 两者回答的是同一个问题，只是批量与单条两种用法。写两遍谓词就意味着将来两处可能不一致，
     * 而调用方一个看列表一个看详情，不一致时表现为「同一篇笔记收藏态对不上」，很难查。
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
        return noteId != null && favoritedNoteIds(userId, List.of(noteId)).contains(noteId);
    }

    /**
     * 一批笔记里，该用户收藏了哪些。列表页用它填 {@code isFavorited}（§5.4）。
     *
     * <p><b>批量而不是逐条</b>：一页 20 条就会变成 20 次往返（N+1）。这里一次
     * {@code IN} 查询取回整页的收藏关系，条数与页大小无关。查询走 {@code uk_user_note}
     * 唯一索引，命中行数至多等于入参个数。
     *
     * <p>返回 {@code Set} 而不是 {@code List}：调用方要的是「在不在集合里」，
     * 不是顺序，也不是条数。用 Set 把这个意图写进返回值，顺带避免调用方写出
     * {@code list.contains(...)} 这种 O(n) 的误用。
     *
     * @param userId  当前登录者，取自 JWT
     * @param noteIds 待判定的笔记 id。空集合直接短路——{@code IN ()} 是语法错误，
     *                不能让它流到 SQL
     * @return 入参中已收藏的 id；未收藏的不出现。无收藏或无有效入参时返回空集合
     */
    public Set<Long> favoritedNoteIds(Long userId, Collection<Long> noteIds) {
        if (userId == null || noteIds.isEmpty()) {
            return Set.of();
        }
        return favoriteMapper.selectList(Wrappers.<Favorite>lambdaQuery()
                        .select(Favorite::getNoteId)
                        .eq(Favorite::getUserId, userId)
                        .in(Favorite::getNoteId, noteIds))
                .stream()
                .map(Favorite::getNoteId)
                .collect(Collectors.toSet());
    }
}
