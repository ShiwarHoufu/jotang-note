package com.wlf.note.domain.favorite;

import com.wlf.common.BusinessException;
import com.wlf.common.ErrorCode;

import java.time.LocalDateTime;

/**
 * 收藏聚合根。
 *
 * <p>去重由 {@code uk_user_note} 兜底；笔记软删除后收藏关系保留。
 */
public final class Favorite {

    private Long id;
    private final Long userId;
    private final Long noteId;
    private final LocalDateTime createdAt;

    private Favorite(Long id, Long userId, Long noteId, LocalDateTime createdAt) {
        this.id = id;
        this.userId = userId;
        this.noteId = noteId;
        this.createdAt = createdAt;
    }

    public static Favorite create(Long userId, Long noteId) {
        requireIds(userId, noteId);
        return new Favorite(null, userId, noteId, null);
    }

    public static Favorite reconstitute(Long id, Long userId, Long noteId, LocalDateTime createdAt) {
        if (id == null || id <= 0) {
            throw new BusinessException(ErrorCode.SERVER_ERROR, "收藏 id 不合法");
        }
        requireIds(userId, noteId);
        return new Favorite(id, userId, noteId, createdAt);
    }

    /** 落库后回填自增 id。 */
    public void assignId(Long id) {
        if (id == null || id <= 0) {
            throw new BusinessException(ErrorCode.SERVER_ERROR, "收藏 id 不合法");
        }
        if (this.id != null) {
            throw new BusinessException(ErrorCode.SERVER_ERROR, "收藏 id 已存在");
        }
        this.id = id;
    }

    private static void requireIds(Long userId, Long noteId) {
        if (userId == null || userId <= 0 || noteId == null || noteId <= 0) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "收藏对象不合法");
        }
    }

    public Long id() {
        return id;
    }

    public Long userId() {
        return userId;
    }

    public Long noteId() {
        return noteId;
    }

    public LocalDateTime createdAt() {
        return createdAt;
    }
}
