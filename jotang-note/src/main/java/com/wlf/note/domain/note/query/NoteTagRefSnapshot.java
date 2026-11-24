package com.wlf.note.domain.note.query;

/** 笔记与标签的关联投影。 */
public record NoteTagRefSnapshot(Long noteId, Long tagId, String tagName) {
}
