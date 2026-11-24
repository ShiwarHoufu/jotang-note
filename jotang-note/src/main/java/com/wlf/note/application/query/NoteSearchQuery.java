package com.wlf.note.application.query;

/** 按标题精确检索。 */
public record NoteSearchQuery(String title, int limit) {
}
