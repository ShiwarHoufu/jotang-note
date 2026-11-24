package com.wlf.note.domain.note;

import com.wlf.common.BusinessException;
import com.wlf.common.ErrorCode;

import java.util.Objects;

/** 笔记的状态与身份判定，无状态领域服务。 */
public final class NoteAccessPolicy {

    private NoteAccessPolicy() {
    }

    public static void requireUploader(Long uploaderId, Long viewerId) {
        if (!Objects.equals(uploaderId, viewerId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    public static void requireEditable(NoteStatus status) {
        if (status == NoteStatus.DELETED) {
            throw new BusinessException(ErrorCode.NOTE_UNAVAILABLE);
        }
    }

    public static void requireVisible(NoteStatus status, Long uploaderId, Long viewerId, boolean viewerIsAdmin) {
        if (status == NoteStatus.DELETED) {
            throw new BusinessException(ErrorCode.NOTE_UNAVAILABLE);
        }
        if (status == NoteStatus.OFFLINE && !viewerIsAdmin && !Objects.equals(uploaderId, viewerId)) {
            throw new BusinessException(ErrorCode.NOTE_UNAVAILABLE);
        }
    }

    public static void requireOnline(NoteStatus status) {
        if (status != NoteStatus.ONLINE) {
            throw new BusinessException(ErrorCode.NOTE_UNAVAILABLE);
        }
    }

    public static void requireNotDeleted(NoteStatus status) {
        if (status == NoteStatus.DELETED) {
            throw new BusinessException(ErrorCode.NOTE_UNAVAILABLE);
        }
    }
}
