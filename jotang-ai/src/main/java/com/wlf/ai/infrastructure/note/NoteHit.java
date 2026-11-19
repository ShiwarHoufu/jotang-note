package com.wlf.ai.infrastructure.note;

/** 一条标题检索命中。 */
public record NoteHit(Long noteId, String title, String teacher, String summary) {
}
