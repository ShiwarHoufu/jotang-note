package com.wlf.note;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 「我的上传」主干查询的一行，{@link NoteMapper#selectMyNotePage} 的返回类型。
 *
 * <p><b>为什么不复用 {@link NoteListRow}</b>：两者的列不重合，而且是两条不同的 SQL。
 * 这里要 {@code status}（公开列表只出 ONLINE，所以那边没有）、要 {@code created_at}
 * （本列表的排序键）、也不要 {@code NoteListRow} 的 {@code updatedAt}；那边则要上传者
 * （{@code nickname} / {@code avatar} / {@code collegeName}），本列表里上传者恒等于本人，一律不需要。
 *
 * <p>标签不在这里：它与笔记是 to-many，并进分页查询会让 {@code LIMIT} 切在 JOIN 之后的行上
 * （一篇笔记有几个标签就占几个名额）。
 * 单独一条 {@code IN} 查询批量取回，由 Service 按 {@code noteId} 装配。
 */
@Getter
@Setter
public class MyNoteRow {

    // ---- note ----
    private Long id;

    /** {@code ONLINE} / {@code OFFLINE}。{@code DELETED} 已在 SQL 里被排除，不会出现 */
    private String status;

    private String title;
    private Long viewCount;
    private Long downloadCount;
    private Long favoriteCount;

    /** 上传时刻，同时是本查询的排序键 */
    private LocalDateTime createdAt;

    // ---- course ----
    private Long courseId;
    private String courseName;
}
