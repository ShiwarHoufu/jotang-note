package com.wlf.note.application.result;

/** 标题精确检索的一条命中。 */
public record NoteSearchHitResult(Long noteId, String title, String teacher, String summary) {
}
