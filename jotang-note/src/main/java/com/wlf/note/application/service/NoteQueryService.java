package com.wlf.note.application.service;

import com.wlf.common.BusinessException;
import com.wlf.common.ErrorCode;
import com.wlf.common.Paging;
import com.wlf.note.application.query.AdminNoteListQuery;
import com.wlf.note.application.query.MyNoteListQuery;
import com.wlf.note.application.query.NoteListQuery;
import com.wlf.note.application.query.NoteSearchQuery;
import com.wlf.note.application.result.AdminNoteItemResult;
import com.wlf.note.application.result.CourseResult;
import com.wlf.note.application.result.MyNoteItemResult;
import com.wlf.note.application.result.NoteDetailResult;
import com.wlf.note.application.result.NoteFileResult;
import com.wlf.note.application.result.NoteListItemResult;
import com.wlf.note.application.result.NoteSearchHitResult;
import com.wlf.note.application.result.PageResult;
import com.wlf.note.application.result.PreviewUrlResult;
import com.wlf.note.application.result.RawContentResult;
import com.wlf.note.application.result.TagResult;
import com.wlf.note.application.result.UploaderResult;
import com.wlf.note.domain.note.NoteAccessPolicy;
import com.wlf.note.domain.note.NoteDetailCache;
import com.wlf.note.domain.note.NoteQueryRepository;
import com.wlf.note.domain.note.NoteRepository;
import com.wlf.note.domain.note.NoteStatus;
import com.wlf.note.domain.note.PreviewMode;
import com.wlf.note.domain.note.query.AdminNoteSnapshot;
import com.wlf.note.domain.note.query.MyNoteSnapshot;
import com.wlf.note.domain.note.query.NoteDetailSnapshot;
import com.wlf.note.domain.note.query.NoteDetailView;
import com.wlf.note.domain.note.query.NoteFileOwnershipSnapshot;
import com.wlf.note.domain.note.query.NoteListCriteria;
import com.wlf.note.domain.note.query.NoteListSnapshot;
import com.wlf.note.domain.note.query.NoteTagRefSnapshot;
import com.wlf.note.domain.shared.PageSlice;
import com.wlf.note.domain.uploader.UploaderProfile;
import com.wlf.note.domain.uploader.UploaderProfileReader;
import com.wlf.storage.Bucket;
import com.wlf.storage.StorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** 笔记读侧用例。 */
@Slf4j
@Service
public class NoteQueryService {

    private static final Duration DETAIL_CACHE_TTL = Duration.ofMinutes(10);
    private static final NoteDetailView ABSENT = new NoteDetailView(null, List.of());
    private static final Duration ABSENT_CACHE_TTL = Duration.ofMinutes(1);
    private static final int MAX_TITLE_SEARCH_LIMIT = 20;
    private static final Duration PREVIEW_URL_TTL = Duration.ofMinutes(5);

    private final NoteRepository noteRepository;
    private final NoteQueryRepository noteQueryRepository;
    private final NoteDetailCache noteDetailCache;
    private final FavoriteService favoriteService;
    private final UploaderProfileReader uploaderProfileReader;
    private final StorageService storageService;

    public NoteQueryService(NoteRepository noteRepository,
                            NoteQueryRepository noteQueryRepository,
                            NoteDetailCache noteDetailCache,
                            FavoriteService favoriteService,
                            UploaderProfileReader uploaderProfileReader,
                            StorageService storageService) {
        this.noteRepository = noteRepository;
        this.noteQueryRepository = noteQueryRepository;
        this.noteDetailCache = noteDetailCache;
        this.favoriteService = favoriteService;
        this.uploaderProfileReader = uploaderProfileReader;
        this.storageService = storageService;
    }

    public NoteDetailResult detail(Long noteId, Long viewerId, boolean viewerIsAdmin) {
        NoteDetailView view = loadMeta(noteId);
        if (view == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        NoteDetailSnapshot row = view.detail();

        NoteStatus status = row.noteStatus();
        NoteAccessPolicy.requireVisible(status, row.uploaderId(), viewerId, viewerIsAdmin);

        noteRepository.incrementViewCount(noteId, NoteStatus.ONLINE);

        UploaderProfile uploader = uploaderProfileReader.batchGet(List.of(row.uploaderId()))
                .get(row.uploaderId());
        return toDetailResult(row, view.tags(), status, viewerId, uploader);
    }

    public PageResult<NoteListItemResult> list(Long viewerId, NoteListQuery query) {
        Paging.requireShallow(query.page(), query.size());

        NoteListCriteria criteria = new NoteListCriteria(
                query.page(),
                query.size(),
                query.courseId(),
                query.tagId(),
                escapeLikeKeyword(query.keyword()),
                query.sort());
        PageSlice<NoteListSnapshot> result = noteQueryRepository.findPage(criteria);

        return new PageResult<>(
                assemble(result.items(), viewerId),
                result.total(),
                query.page(),
                query.size());
    }

    public PageResult<MyNoteItemResult> listMine(Long uploaderId, MyNoteListQuery query) {
        Paging.requireShallow(query.page(), query.size());

        PageSlice<MyNoteSnapshot> result = noteQueryRepository.findMine(
                uploaderId, query.page(), query.size());

        return new PageResult<>(
                assembleMyNotes(result.items(), uploaderId),
                result.total(),
                query.page(),
                query.size());
    }

    public PageResult<AdminNoteItemResult> listOffline(AdminNoteListQuery query) {
        Paging.requireShallow(query.page(), query.size());

        PageSlice<AdminNoteSnapshot> result = noteQueryRepository.findOfflinePage(
                query.page(), query.size(), escapeLikeKeyword(query.keyword()));

        return new PageResult<>(
                assembleOffline(result.items()),
                result.total(),
                query.page(),
                query.size());
    }

    public NoteFileResult requireOwnFile(Long noteId, Long uploaderId) {
        NoteFileOwnershipSnapshot row = noteQueryRepository.findFileOwnership(noteId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        NoteAccessPolicy.requireUploader(row.uploaderId(), uploaderId);
        NoteAccessPolicy.requireNotDeleted(row.noteStatus());
        return toFileResult(row);
    }

    public NoteFileResult readableOnlineFile(Long noteId) {
        NoteFileOwnershipSnapshot row = noteQueryRepository.findFileOwnership(noteId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        NoteAccessPolicy.requireOnline(row.noteStatus());
        return toFileResult(row);
    }

    public List<NoteSearchHitResult> search(NoteSearchQuery query) {
        if (query.title() == null || query.title().isBlank()) {
            return List.of();
        }
        int capped = Math.min(Math.max(query.limit(), 1), MAX_TITLE_SEARCH_LIMIT);
        return noteQueryRepository.findOnlineByExactTitle(query.title().strip(), capped).stream()
                .map(row -> new NoteSearchHitResult(row.id(), row.title(), row.teacher(), row.summary()))
                .toList();
    }

    /** 预览与详情页同一可见性：ONLINE 所有人，OFFLINE 仅本人/管理员，DELETED 无人。 */
    public PreviewUrlResult previewUrl(Long noteId, Long viewerId, boolean viewerIsAdmin) {
        NoteFileOwnershipSnapshot row = noteQueryRepository.findFileOwnership(noteId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        NoteAccessPolicy.requireVisible(row.noteStatus(), row.uploaderId(), viewerId, viewerIsAdmin);
        if (PreviewMode.of(row.contentType()) != PreviewMode.IMAGE_INLINE) {
            throw new BusinessException(ErrorCode.FILE_INVALID, "该类型不支持内联预览，请下载后查看");
        }
        return new PreviewUrlResult(
                storageService.presignedUrl(Bucket.NOTE, row.storageKey(), PREVIEW_URL_TTL, null));
    }

    /** 与 {@link #previewUrl} 同一可见性规则。 */
    public RawContentResult openRaw(Long noteId, Long viewerId, boolean viewerIsAdmin) {
        NoteFileOwnershipSnapshot row = noteQueryRepository.findFileOwnership(noteId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        NoteAccessPolicy.requireVisible(row.noteStatus(), row.uploaderId(), viewerId, viewerIsAdmin);
        if (PreviewMode.of(row.contentType()) != PreviewMode.TEXT_PROXY) {
            throw new BusinessException(ErrorCode.FILE_INVALID, "该类型不支持文本预览，请下载后查看");
        }
        return new RawContentResult(row.size(), storageService.open(Bucket.NOTE, row.storageKey()));
    }

    private NoteDetailResult toDetailResult(NoteDetailSnapshot row,
                                            List<NoteTagRefSnapshot> tags,
                                            NoteStatus status,
                                            Long viewerId,
                                            UploaderProfile uploader) {
        return new NoteDetailResult(
                row.id(),
                row.title(),
                row.summary(),
                row.teacher(),
                status,
                new CourseResult(row.courseId(), row.courseName()),
                uploaderOf(row.uploaderId(), uploader),
                tags.stream().map(ref -> new TagResult(ref.tagId(), ref.tagName())).toList(),
                row.viewCount(),
                row.downloadCount(),
                row.favoriteCount(),
                row.createdAt(),
                row.updatedAt(),
                fileInfoOf(row),
                favoriteService.isFavorited(viewerId, row.id()));
    }

    private NoteDetailResult.FileInfo fileInfoOf(NoteDetailSnapshot row) {
        return new NoteDetailResult.FileInfo(
                row.originalName(),
                row.size(),
                row.contentType(),
                PreviewMode.of(row.contentType()));
    }

    private NoteDetailView loadMeta(Long noteId) {
        NoteDetailView cached = null;
        try {
            cached = noteDetailCache.get(noteId);
        } catch (RuntimeException e) {
            log.warn("读笔记详情缓存失败，降级直查库 noteId={}", noteId, e);
        }
        if (cached != null) {
            return cached.detail() == null ? null : cached;
        }

        NoteDetailSnapshot row = noteQueryRepository.findDetail(noteId).orElse(null);
        if (row == null) {
            cacheQuietly(noteId, ABSENT, ABSENT_CACHE_TTL);
            return null;
        }
        NoteDetailView loaded = new NoteDetailView(row, noteQueryRepository.findTags(noteId));
        cacheQuietly(noteId, loaded, DETAIL_CACHE_TTL);
        return loaded;
    }

    private void cacheQuietly(Long noteId, NoteDetailView view, Duration ttl) {
        try {
            noteDetailCache.put(noteId, view, ttl);
        } catch (RuntimeException e) {
            log.warn("回填笔记详情缓存失败，不影响本次请求 noteId={}", noteId, e);
        }
    }

    private List<NoteListItemResult> assemble(List<NoteListSnapshot> rows, Long viewerId) {
        if (rows.isEmpty()) {
            return List.of();
        }
        List<Long> noteIds = rows.stream().map(NoteListSnapshot::id).toList();
        List<Long> uploaderIds = rows.stream().map(NoteListSnapshot::uploaderId).distinct().toList();

        Map<Long, List<TagResult>> tagsByNote = tagsGroupedByNote(noteIds);
        Map<Long, UploaderProfile> profiles = uploaderProfileReader.batchGet(uploaderIds);
        Set<Long> favoritedNoteIds = favoriteService.favoritedNoteIds(viewerId, noteIds);

        return rows.stream()
                .map(row -> new NoteListItemResult(
                        row.id(),
                        row.title(),
                        new CourseResult(row.courseId(), row.courseName()),
                        uploaderOf(row.uploaderId(), profiles.get(row.uploaderId())),
                        tagsByNote.getOrDefault(row.id(), List.of()),
                        row.viewCount(),
                        row.downloadCount(),
                        row.favoriteCount(),
                        row.updatedAt(),
                        favoritedNoteIds.contains(row.id())))
                .toList();
    }

    private List<MyNoteItemResult> assembleMyNotes(List<MyNoteSnapshot> rows, Long viewerId) {
        if (rows.isEmpty()) {
            return List.of();
        }
        List<Long> noteIds = rows.stream().map(MyNoteSnapshot::id).toList();
        Map<Long, List<TagResult>> tagsByNote = tagsGroupedByNote(noteIds);
        Set<Long> favoritedNoteIds = favoriteService.favoritedNoteIds(viewerId, noteIds);

        return rows.stream()
                .map(row -> new MyNoteItemResult(
                        row.id(),
                        row.noteStatus(),
                        row.title(),
                        new CourseResult(row.courseId(), row.courseName()),
                        tagsByNote.getOrDefault(row.id(), List.of()),
                        row.viewCount(),
                        row.downloadCount(),
                        row.favoriteCount(),
                        row.createdAt(),
                        favoritedNoteIds.contains(row.id())))
                .toList();
    }

    private List<AdminNoteItemResult> assembleOffline(List<AdminNoteSnapshot> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        List<Long> noteIds = rows.stream().map(AdminNoteSnapshot::id).toList();
        List<Long> uploaderIds = rows.stream().map(AdminNoteSnapshot::uploaderId).distinct().toList();

        Map<Long, List<TagResult>> tagsByNote = tagsGroupedByNote(noteIds);
        Map<Long, UploaderProfile> profiles = uploaderProfileReader.batchGet(uploaderIds);

        return rows.stream()
                .map(row -> new AdminNoteItemResult(
                        row.id(),
                        row.title(),
                        new CourseResult(row.courseId(), row.courseName()),
                        uploaderOf(row.uploaderId(), profiles.get(row.uploaderId())),
                        tagsByNote.getOrDefault(row.id(), List.of()),
                        row.viewCount(),
                        row.downloadCount(),
                        row.favoriteCount(),
                        row.createdAt(),
                        row.updatedAt()))
                .toList();
    }

    private Map<Long, List<TagResult>> tagsGroupedByNote(List<Long> noteIds) {
        return noteQueryRepository.findTagsByNoteIds(noteIds).stream()
                .collect(Collectors.groupingBy(NoteTagRefSnapshot::noteId,
                        Collectors.mapping(ref -> new TagResult(ref.tagId(), ref.tagName()),
                                Collectors.toList())));
    }

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

    private static NoteFileResult toFileResult(NoteFileOwnershipSnapshot row) {
        return new NoteFileResult(row.storageKey(), row.size(), row.contentType());
    }

    private static String escapeLikeKeyword(String keyword) {
        if (keyword == null) {
            return null;
        }
        String trimmed = keyword.strip();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.replace("/", "//").replace("%", "/%").replace("_", "/_");
    }
}
