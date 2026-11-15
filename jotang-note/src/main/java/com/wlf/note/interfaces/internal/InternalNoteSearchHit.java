package com.wlf.note.interfaces.internal;

/**
 * 内部接口返回的标题检索命中。
 */
public record InternalNoteSearchHit(Long noteId, String title, String teacher, String summary) {
}
