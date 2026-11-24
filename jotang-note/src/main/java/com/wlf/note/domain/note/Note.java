package com.wlf.note.domain.note;

import com.wlf.common.BusinessException;
import com.wlf.common.ErrorCode;
import com.wlf.note.domain.note.model.CourseId;
import com.wlf.note.domain.note.model.NoteSummary;
import com.wlf.note.domain.note.model.NoteTitle;
import com.wlf.note.domain.note.model.TeacherName;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 笔记聚合根。
 *
 * <p>附件与标签都属于本聚合；计数列只读取快照，写走仓储的原子更新。
 * 时间戳由数据库维护，聚合不持有时间源。
 */
public final class Note {

    private Long id;
    private final Long uploaderId;
    private CourseId courseId;
    private NoteTitle title;
    private NoteSummary summary;
    private TeacherName teacher;
    private NoteStatus status;
    private final long viewCount;
    private final long downloadCount;
    private final long favoriteCount;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;
    private final LocalDateTime deletedAt;
    private final NoteFile file;
    private Set<Long> tagIds;

    private Note(Long id,
                 Long uploaderId,
                 CourseId courseId,
                 NoteTitle title,
                 NoteSummary summary,
                 TeacherName teacher,
                 NoteStatus status,
                 long viewCount,
                 long downloadCount,
                 long favoriteCount,
                 LocalDateTime createdAt,
                 LocalDateTime updatedAt,
                 LocalDateTime deletedAt,
                 NoteFile file,
                 Set<Long> tagIds) {
        this.id = id;
        this.uploaderId = uploaderId;
        this.courseId = courseId;
        this.title = title;
        this.summary = summary;
        this.teacher = teacher;
        this.status = status;
        this.viewCount = viewCount;
        this.downloadCount = downloadCount;
        this.favoriteCount = favoriteCount;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.deletedAt = deletedAt;
        this.file = file;
        this.tagIds = tagIds;
    }

    /** 上传成功即上架。 */
    public static Note upload(Long uploaderId,
                              CourseId courseId,
                              NoteTitle title,
                              NoteSummary summary,
                              TeacherName teacher,
                              NoteFile file,
                              Collection<Long> tagIds) {
        requireUploaderShape(uploaderId);
        requireMetadata(courseId, title, summary, teacher);
        if (file == null) {
            throw new BusinessException(ErrorCode.SERVER_ERROR, "笔记缺少附件");
        }
        return new Note(null, uploaderId, courseId, title, summary, teacher, NoteStatus.ONLINE,
                0, 0, 0, null, null, null, file, normalizeTagIds(tagIds));
    }

    public static Note reconstitute(Long id,
                                    Long uploaderId,
                                    CourseId courseId,
                                    NoteTitle title,
                                    NoteSummary summary,
                                    TeacherName teacher,
                                    NoteStatus status,
                                    long viewCount,
                                    long downloadCount,
                                    long favoriteCount,
                                    LocalDateTime createdAt,
                                    LocalDateTime updatedAt,
                                    LocalDateTime deletedAt,
                                    NoteFile file,
                                    Collection<Long> tagIds) {
        if (id == null || id <= 0) {
            throw new BusinessException(ErrorCode.SERVER_ERROR, "笔记 id 不合法");
        }
        requireUploaderShape(uploaderId);
        requireMetadata(courseId, title, summary, teacher);
        if (status == null) {
            throw new BusinessException(ErrorCode.SERVER_ERROR, "笔记状态不合法");
        }
        if (viewCount < 0 || downloadCount < 0 || favoriteCount < 0) {
            throw new BusinessException(ErrorCode.SERVER_ERROR, "笔记计数不合法");
        }
        if (file == null) {
            throw new BusinessException(ErrorCode.SERVER_ERROR, "笔记缺少附件");
        }
        return new Note(id, uploaderId, courseId, title, summary, teacher, status,
                viewCount, downloadCount, favoriteCount, createdAt, updatedAt, deletedAt,
                file, normalizeTagIds(tagIds));
    }

    /** 编辑元数据；标签全量替换。 */
    public void changeMetadata(Long editorId,
                               NoteTitle title,
                               NoteSummary summary,
                               TeacherName teacher,
                               CourseId courseId,
                               Collection<Long> tagIds) {
        NoteAccessPolicy.requireUploader(uploaderId, editorId);
        NoteAccessPolicy.requireEditable(status);
        requireMetadata(courseId, title, summary, teacher);
        this.title = title;
        this.summary = summary;
        this.teacher = teacher;
        this.courseId = courseId;
        this.tagIds = normalizeTagIds(tagIds);
    }

    /** 软删除；已删除时幂等。 */
    public void delete(Long editorId) {
        NoteAccessPolicy.requireUploader(uploaderId, editorId);
        this.status = NoteStatus.DELETED;
    }

    /** 管理员下架；已下架时幂等。 */
    public void offline() {
        NoteAccessPolicy.requireNotDeleted(status);
        this.status = NoteStatus.OFFLINE;
    }

    /** 管理员恢复；已上架时幂等。 */
    public void restore() {
        NoteAccessPolicy.requireNotDeleted(status);
        this.status = NoteStatus.ONLINE;
    }

    public void ensureUploader(Long viewerId) {
        NoteAccessPolicy.requireUploader(uploaderId, viewerId);
    }

    public void ensureVisibleTo(Long viewerId, boolean viewerIsAdmin) {
        NoteAccessPolicy.requireVisible(status, uploaderId, viewerId, viewerIsAdmin);
    }

    public void ensureEditable() {
        NoteAccessPolicy.requireEditable(status);
    }

    public boolean isOnline() {
        return status == NoteStatus.ONLINE;
    }

    public boolean isDeleted() {
        return status == NoteStatus.DELETED;
    }

    /** 落库后回填自增 id。 */
    public void assignId(Long id) {
        if (id == null || id <= 0) {
            throw new BusinessException(ErrorCode.SERVER_ERROR, "笔记 id 不合法");
        }
        if (this.id != null) {
            throw new BusinessException(ErrorCode.SERVER_ERROR, "笔记 id 已存在");
        }
        this.id = id;
    }

    private static void requireUploaderShape(Long uploaderId) {
        if (uploaderId == null || uploaderId <= 0) {
            throw new BusinessException(ErrorCode.SERVER_ERROR, "上传者不合法");
        }
    }

    private static void requireMetadata(CourseId courseId, NoteTitle title,
                                        NoteSummary summary, TeacherName teacher) {
        if (courseId == null || title == null || summary == null || teacher == null) {
            throw new BusinessException(ErrorCode.SERVER_ERROR, "笔记信息不完整");
        }
    }

    private static Set<Long> normalizeTagIds(Collection<Long> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            return Set.of();
        }
        LinkedHashSet<Long> copy = new LinkedHashSet<>();
        for (Long tagId : tagIds) {
            if (tagId == null || tagId <= 0) {
                throw new BusinessException(ErrorCode.SERVER_ERROR, "标签 id 不合法");
            }
            copy.add(tagId);
        }
        return Collections.unmodifiableSet(copy);
    }

    public Long id() {
        return id;
    }

    public Long uploaderId() {
        return uploaderId;
    }

    public CourseId courseId() {
        return courseId;
    }

    public NoteTitle title() {
        return title;
    }

    public NoteSummary summary() {
        return summary;
    }

    public TeacherName teacher() {
        return teacher;
    }

    public NoteStatus status() {
        return status;
    }

    public long viewCount() {
        return viewCount;
    }

    public long downloadCount() {
        return downloadCount;
    }

    public long favoriteCount() {
        return favoriteCount;
    }

    public LocalDateTime createdAt() {
        return createdAt;
    }

    public LocalDateTime updatedAt() {
        return updatedAt;
    }

    public LocalDateTime deletedAt() {
        return deletedAt;
    }

    public NoteFile file() {
        return file;
    }

    public Set<Long> tagIds() {
        return tagIds;
    }
}
