package com.wlf.note.application.service;

import com.wlf.common.BusinessException;
import com.wlf.common.ErrorCode;
import com.wlf.common.Paging;
import com.wlf.note.application.command.FavoriteNoteCommand;
import com.wlf.note.application.query.FavoriteListQuery;
import com.wlf.note.application.result.CourseResult;
import com.wlf.note.application.result.FavoriteCountResult;
import com.wlf.note.application.result.FavoriteItemResult;
import com.wlf.note.application.result.PageResult;
import com.wlf.note.application.result.TagResult;
import com.wlf.note.application.result.UploaderResult;
import com.wlf.note.domain.favorite.Favorite;
import com.wlf.note.domain.favorite.FavoriteQueryRepository;
import com.wlf.note.domain.favorite.FavoriteRepository;
import com.wlf.note.domain.favorite.query.FavoriteItemSnapshot;
import com.wlf.note.domain.note.NoteAccessPolicy;
import com.wlf.note.domain.note.NoteQueryRepository;
import com.wlf.note.domain.note.NoteRepository;
import com.wlf.note.domain.note.NoteStatus;
import com.wlf.note.domain.note.query.NoteTagRefSnapshot;
import com.wlf.note.domain.shared.PageSlice;
import com.wlf.note.domain.uploader.UploaderProfile;
import com.wlf.note.domain.uploader.UploaderProfileReader;
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

    private final FavoriteRepository favoriteRepository;
    private final FavoriteQueryRepository favoriteQueryRepository;

    /**
     * 只为维护 {@code note.favorite_count} 与取那行锁而存在。
     *
     * <p><b>不注入 {@code NoteQueryService}</b>：{@code NoteQueryService} 已经注入本类（详情页要填
     * {@code isFavorited}），反向注入即是构造器循环依赖，应用连启动都起不来。
     */
    private final NoteRepository noteRepository;
    private final NoteQueryRepository noteQueryRepository;

    private final UploaderProfileReader uploaderProfileReader;

    /**
     * 收藏列表要展示上传者，头像得拼成公网直连 URL（§6.7）。
     * 与查询服务注入的是同一个接口，头像是公共读 Bucket，不需要签名。
     */
    private final StorageService storageService;

    public FavoriteService(FavoriteRepository favoriteRepository,
                           FavoriteQueryRepository favoriteQueryRepository,
                           NoteRepository noteRepository,
                           NoteQueryRepository noteQueryRepository,
                           UploaderProfileReader uploaderProfileReader,
                           StorageService storageService) {
        this.favoriteRepository = favoriteRepository;
        this.favoriteQueryRepository = favoriteQueryRepository;
        this.noteRepository = noteRepository;
        this.noteQueryRepository = noteQueryRepository;
        this.uploaderProfileReader = uploaderProfileReader;
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
    public FavoriteCountResult favorite(FavoriteNoteCommand command) {
        Long userId = command.userId();
        Long noteId = command.noteId();
        NoteStatus status = noteRepository.lockStatus(noteId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        NoteAccessPolicy.requireOnline(status);

        Favorite favorite = Favorite.create(userId, noteId);
        try {
            favoriteRepository.insert(favorite);
        } catch (DuplicateKeyException e) {
            // 去重由 uk_user_note 兜底，不做「先查再插」——那套在并发下会漏，
            // 两个请求可以同时查到「没收藏过」（§3.3）。异常继续外抛而不是吞掉，
            // 事务照常回滚；此时第 3 步还没执行，没有任何东西要撤销。
            throw new BusinessException(ErrorCode.ALREADY_FAVORITED);
        }

        noteRepository.incrementFavoriteCount(noteId);
        // 笔记行被上面的 FOR UPDATE 锁着且确实存在
        return new FavoriteCountResult(noteRepository.favoriteCount(noteId));
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
    public FavoriteCountResult unfavorite(FavoriteNoteCommand command) {
        Long userId = command.userId();
        Long noteId = command.noteId();
        // 返回值是状态，这里用不上——要的是这把排他锁与「行存在吗」这一个事实
        noteRepository.lockStatus(noteId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        int deleted = favoriteRepository.deleteByUserAndNote(userId, noteId);

        if (deleted > 0) {
            // 只有真删掉了关系才减计数。无条件减会让「连点两次取消」把计数多减一次
            noteRepository.decrementFavoriteCount(noteId);
        }

        return new FavoriteCountResult(noteRepository.favoriteCount(noteId));
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
    public PageResult<FavoriteItemResult> listFavorites(Long userId, FavoriteListQuery query) {
        Paging.requireShallow(query.page(), query.size());

        PageSlice<FavoriteItemSnapshot> result = favoriteQueryRepository.findPage(
                userId, query.page(), query.size());

        return new PageResult<>(
                assembleFavorites(result.items()),
                result.total(),
                query.page(),
                query.size());
    }

    private List<FavoriteItemResult> assembleFavorites(List<FavoriteItemSnapshot> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        List<Long> noteIds = rows.stream().map(FavoriteItemSnapshot::noteId).toList();

        Map<Long, List<TagResult>> tagsByNote = noteQueryRepository.findTagsByNoteIds(noteIds).stream()
                .collect(Collectors.groupingBy(NoteTagRefSnapshot::noteId,
                        Collectors.mapping(ref -> new TagResult(ref.tagId(), ref.tagName()),
                                Collectors.toList())));
        List<Long> uploaderIds = rows.stream().map(FavoriteItemSnapshot::uploaderId).distinct().toList();
        Map<Long, UploaderProfile> profiles = uploaderProfileReader.batchGet(uploaderIds);

        return rows.stream()
                .map(row -> toFavoriteItem(row, tagsByNote, profiles))
                .toList();
    }

    private FavoriteItemResult toFavoriteItem(FavoriteItemSnapshot row,
                                              Map<Long, List<TagResult>> tagsByNote,
                                              Map<Long, UploaderProfile> profiles) {
        NoteStatus status = NoteStatus.fromName(row.status());
        CourseResult course = new CourseResult(row.courseId(), row.courseName());

        if (status != NoteStatus.ONLINE) {
            return new FavoriteItemResult(row.noteId(), status, row.favoritedAt(),
                    row.title(), course,
                    null, List.of(), null, null, null, null);
        }

        return new FavoriteItemResult(row.noteId(), status, row.favoritedAt(),
                row.title(), course,
                uploaderOf(row.uploaderId(), profiles.get(row.uploaderId())),
                // 没有标签时给空列表：getOrDefault 的默认值必须是不可变的 List.of()，
                // 而不是 null——前端可以无条件遍历
                tagsByNote.getOrDefault(row.noteId(), List.of()),
                row.viewCount(), row.downloadCount(), row.favoriteCount(),
                row.updatedAt());
    }

    /**
     * 头像对象键 → 公网直连 URL。
     */
    private UploaderResult uploaderOf(Long uploaderId, UploaderProfile uploader) {
        if (uploader == null) {
            return new UploaderResult(uploaderId, null, null, null);
        }
        return new UploaderResult(uploaderId, uploader.nickname(),
                avatarUrl(uploader.avatarKey()), uploader.collegeName());
    }

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
        return favoriteQueryRepository.findFavoritedNoteIds(userId, noteIds);
    }
}
