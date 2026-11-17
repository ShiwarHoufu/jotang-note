package com.wlf.ai.infrastructure.client.note;

/**
 * note 内部接口的标题检索命中。
 */
public record InternalNoteSearchHit(Long noteId, String title, String teacher, String summary) {
}
