package com.wlf.note.application.note.dto;

/**
 * 「按标题精确检索」的一条命中，{@link NoteService#findOnlineByExactTitle} 的返回类型。
 */
public record NoteSearchHit(Long noteId, String title, String teacher, String summary) {
}
