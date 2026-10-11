package com.wlf.note;

/**
 * 「按标题精确检索」的一条命中，{@link NoteService#findOnlineByExactTitle} 的返回类型。
 *
 * <p>用 record 而不是 {@link NoteListRow} 那种 {@code @Getter @Setter}：那个形状是给
 * MyBatis 结果映射用的，而这条是 Service 在 Java 侧从 {@link com.wlf.entity.Note} 折出来的，
 * 没有映射需求。
 *
 * <p>刻意不带 {@code status} / 计数 / 上传者 / {@code storage_key}：消费它的是模型，
 * 与「总结这篇笔记」无关的字段没有理由进上下文。
 */
public record NoteSearchHit(Long noteId, String title, String teacher, String summary) {
}
