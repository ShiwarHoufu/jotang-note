package com.wlf.note.domain.note.query;

/** 标题精确检索的投影。 */
public record NoteSearchSnapshot(Long id, String title, String teacher, String summary) {
}
