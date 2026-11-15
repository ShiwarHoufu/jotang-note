package com.wlf.ai.port;

/**
 * 按标题精确检索的一条命中。
 */
public record NoteSearchHit(Long noteId, String title, String teacher, String summary) {
}
