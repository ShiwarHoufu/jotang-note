package com.wlf.favorite;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wlf.catalog.dto.CourseResponse;
import com.wlf.catalog.dto.TagResponse;
import com.wlf.common.BusinessException;
import com.wlf.common.ErrorCode;
import com.wlf.common.PageResponse;
import com.wlf.common.Paging;
import com.wlf.entity.Favorite;
import com.wlf.favorite.dto.FavoriteItemResponse;
import com.wlf.favorite.dto.FavoriteListQuery;
import com.wlf.note.NoteMapper;
import com.wlf.note.NoteStatus;
import com.wlf.note.NoteTagRef;
import com.wlf.note.dto.UploaderResponse;
import com.wlf.storage.Bucket;
import com.wlf.storage.StorageService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 收藏业务：仅 ONLINE 可收藏；
 * 列表按 {@code note.status} 渲染「已下架 / 已删除」占位。
 *
 * <p>三个入口：{@link #favorite} / {@link #unfavorite} 是写入侧，
 * {@link #listFavorites} 是「我的收藏」；另有两个给别的模块用的只读方法
 * （{@link #isFavorited} / {@link #favoritedNoteIds}），供详情页与笔记列表填 {@code isFavorited}。
 */
@Service
public class FavoriteService {

    private final FavoriteMapper favoriteMapper;

    /**
     * 只为维护 {@code note.favorite_count} 与取那行锁而存在。
     *
     * <p><b>不注入 {@code NoteService}</b>：{@code NoteService} 已经注入本类（详情页要填
     * {@code isFavorited}），反向注入即是构造器循环依赖，应用连启动都起不来。
     * {@code NoteService} 的类注释里也留了同样的警告。
     */
    private final NoteMapper noteMapper;

    /**
     * 收藏列表要展示上传者，头像得拼成公网直连 URL（§6.7）。
     * 与 {@code NoteService} 注入的是同一个接口，头像是公共读 Bucket，不需要签名。
     */
    private final StorageService storageService;

    public FavoriteService(FavoriteMapper favoriteMapper,
                           NoteMapper noteMapper,
                           StorageService storageService) {
        this.favoriteMapper = favoriteMapper;
        this.noteMapper = noteMapper;
        this.storageService = storageService;
    }

    /**
     * 收藏一篇笔记，返回操作后的最新收藏量。
     *
     * <p>只允许收藏 {@code ONLINE} 的笔记
     * @param userId 收藏者，取自 JWT
     * @throws BusinessException 40400 笔记不存在；40301 笔记非 ONLINE；40902 已收藏过
     */
    @Transactional
    public long favorite(Long userId, Long noteId) {
        String status = noteMapper.selectStatusForUpdate(noteId);
        if (status == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        if (!NoteStatus.ONLINE.name().equals(status)) {
            throw new BusinessException(ErrorCode.NOTE_UNAVAILABLE);
        }

        Favorite favorite = new Favorite();
        favorite.setUserId(userId);
        favorite.setNoteId(noteId);
        try {
            favoriteMapper.insert(favorite);
        } catch (DuplicateKeyException e) {
            // 去重由 uk_user_note 兜底，不做「先查再插」——那套在并发下会漏，
            // 两个请求可以同时查到「没收藏过」（§3.3）。异常继续外抛而不是吞掉，
            // 事务照常回滚；此时第 3 步还没执行，没有任何东西要撤销。
            throw new BusinessException(ErrorCode.ALREADY_FAVORITED);
        }

        noteMapper.incrementFavoriteCount(noteId);
        // 笔记行被上面的 FOR UPDATE 锁着且确实存在，这条读不可能为 null
        return noteMapper.selectFavoriteCount(noteId);
    }

    /**
     * 取消收藏，返回操作后的最新收藏量。
     *
     * <p>幂等：本来就没收藏过也返回 200，而不是 40400。
     *
     * <p>笔记非 ONLINE 也放行。
     * 由于软删除，占位项如果不允许取消，用户就永远清不掉自己收藏列表里的已删除/下架的项
     *
     * <p>第一步取锁而不取状态：本方法不关心 {@code ONLINE}，但要与
     * {@link #favorite} 走同一把锁的同一获取顺序（note → favorite），否则收藏与取消收藏
     * 并发作用于同一 {@code (user, note)} 时锁序相反，一样会死锁。
     *
     * @param userId 取消者，取自 JWT
     * @throws BusinessException 40400 笔记不存在。注意「存在但没收藏过」是 200，两者要分清
     */
    @Transactional
    public long unfavorite(Long userId, Long noteId) {
        // 返回值是状态，这里用不上——要的是这把排他锁与「行存在吗」这一个事实
        if (noteMapper.selectStatusForUpdate(noteId) == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }

        int deleted = favoriteMapper.delete(Wrappers.<Favorite>lambdaQuery()
                .eq(Favorite::getUserId, userId)
                .eq(Favorite::getNoteId, noteId));

        if (deleted > 0) {
            // 只有真删掉了关系才减计数。无条件减会让「连点两次取消」把计数多减一次
            noteMapper.decrementFavoriteCount(noteId);
        }

        return noteMapper.selectFavoriteCount(noteId);
    }

    /**
     * 我的收藏，按收藏时间倒序分页。
     *
     * <p>只查自己的收藏：{@code userId} 取自 JWT。
     * （未来实现让用户选择是否公开自己的收藏？）
     *
     * <p><b>三种状态都渲染</b>（§6.5）：{@code ONLINE} 正常项，
     * {@code OFFLINE} / {@code DELETED} 是占位项——保留标题与课程，其余置空。
     *
     * @param userId 当前登录者，取自 JWT
     * @throws BusinessException 40001 分页过深（{@code page × size > 1000}，见 {@link Paging#MAX_PAGE_DEPTH}）
     */
    public PageResponse<FavoriteItemResponse> listFavorites(Long userId, FavoriteListQuery query) {
        Paging.requireShallow(query.getPage(), query.getSize());

        // Page 只携带「第几页、每页几条」，count 语句由分页插件改写生成，不在这里手写
        IPage<FavoriteItemRow> result = favoriteMapper.selectFavoritePage(
                new Page<>(query.getPage(), query.getSize()), userId);

        // total 取插件回填的真实总数；page/size 回显入参而不是 result.getCurrent()，
        // 免得前端收到一个与它请求时不一致的页码（插件内部对越界页有它自己的处理）
        return new PageResponse<>(
                assembleFavorites(result.getRecords()),
                result.getTotal(),
                query.getPage(),
                query.getSize());
    }

    /**
     * 把一页的行装配成出参
     */
    private List<FavoriteItemResponse> assembleFavorites(List<FavoriteItemRow> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        List<Long> noteIds = rows.stream().map(FavoriteItemRow::getNoteId).toList();

        // groupingBy 的下游是 toList（ArrayList），组内保持查询的 encounter order，
        // 于是每篇笔记的标签仍是 tag.id 升序——与详情页、列表页那条查询的口径一致
        Map<Long, List<TagResponse>> tagsByNote = noteMapper.selectTagRefsByNoteIds(noteIds).stream()
                .collect(Collectors.groupingBy(NoteTagRef::getNoteId,
                        Collectors.mapping(ref -> new TagResponse(ref.getTagId(), ref.getTagName()),
                                Collectors.toList())));

        return rows.stream()
                .map(row -> toFavoriteItem(row, tagsByNote))
                .toList();
    }

    /**
     * 一行 → 一项。
     */
    private FavoriteItemResponse toFavoriteItem(FavoriteItemRow row, Map<Long, List<TagResponse>> tagsByNote) {
        NoteStatus status = NoteStatus.valueOf(row.getStatus());
        CourseResponse course = new CourseResponse(row.getCourseId(), row.getCourseName());

        if (status != NoteStatus.ONLINE) {
            return new FavoriteItemResponse(row.getNoteId(), status, row.getFavoritedAt(),
                    row.getTitle(), course,
                    null, List.of(), null, null, null, null);
        }

        return new FavoriteItemResponse(row.getNoteId(), status, row.getFavoritedAt(),
                row.getTitle(), course,
                new UploaderResponse(row.getUploaderId(), row.getNickname(),
                        avatarUrl(row.getAvatar()), row.getCollegeName()),
                // 没有标签时给空列表：getOrDefault 的默认值必须是不可变的 List.of()，
                // 而不是 null——前端可以无条件遍历
                tagsByNote.getOrDefault(row.getNoteId(), List.of()),
                row.getViewCount(), row.getDownloadCount(), row.getFavoriteCount(),
                row.getUpdatedAt());
    }

    /**
     * 头像对象键 → 公网直连 URL。
     */
    private String avatarUrl(String avatarKey) {
        return avatarKey == null ? null : storageService.publicUrl(Bucket.AVATAR, avatarKey);
    }

    /**
     * 某个用户是否收藏了某篇笔记。详情页用它填 {@code isFavorited}。
     *
     * @param userId 当前登录者，取自 JWT
     * @param noteId 任意笔记 id，<b>允许是不存在的 id</b>——「笔记不存在」与
     *               「笔记存在但没收藏」对调用方的含义相同，都是 false。
     *               笔记存在与否由详情接口自己判定（它要为此报 40400），
     *               本方法只回答收藏关系这一个问题，不越界去判另一件事
     */
    public boolean isFavorited(Long userId, Long noteId) {
        return noteId != null && favoritedNoteIds(userId, List.of(noteId)).contains(noteId);
    }

    /**
     * 一批笔记里，该用户收藏了哪些。
     *
     * <p><b>批量而不是逐条</b>：一页 20 条就会变成 20 次往返（N+1）。
     *
     * <p>返回 {@code Set} 而不是 {@code List}：调用方要的是「在不在集合里」，
     * 不是顺序，也不是条数。
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
