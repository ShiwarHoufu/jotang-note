package com.wlf.note;

/**
 * 「按标题精确检索」的一条命中，{@link NoteService#findOnlineByExactTitle} 的返回类型。
 *
 * <p>刻意不带 {@code status} / 计数 / 上传者 / {@code storage_key}：消费它的是模型，
 * 与「总结这篇笔记」无关的字段没有理由进上下文。
 */
public record NoteSearchHit(Long noteId, String title, String teacher, String summary) {
}
