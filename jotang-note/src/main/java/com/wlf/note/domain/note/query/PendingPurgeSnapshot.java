package com.wlf.note.domain.note.query;

/** 待清理的孤儿对象。 */
public record PendingPurgeSnapshot(Long noteId, String storageKey) {
}
