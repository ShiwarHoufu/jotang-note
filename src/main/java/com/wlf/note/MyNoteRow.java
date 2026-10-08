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
 * <p>这不算「同一规则写了两份」：投影类是<b>某一条 SQL 的形状</b>，不是某条业务规则的副本。
 * 真正共享的东西（{@code course} / {@code tags} 的形状）已经复用了
 * {@code CourseResponse} / {@code TagResponse}。既存先例是 {@code FavoriteItemRow}——
 * 它与 {@code NoteListRow} 重叠得比本类还多，当初也选择了单开。
 *
 * <p><b>用 setter 而不是 record</b>：位置绑定的 record 在相邻两列类型相同时，
 * 一旦顺序写反就会静默接错，而 {@code viewCount} / {@code downloadCount} / {@code favoriteCount}
 * 正是三个紧挨着的 {@code Long}。理由同 {@code NoteTagRef} / {@code NoteListRow}。
 *
 * <p>字段名与列的对应靠 MyBatis-Plus 默认开启的 {@code mapUnderscoreToCamelCase}，
 * 重名列（{@code id} / {@code name}）必须在 SQL 里显式起别名。
 *
 * <p>标签不在这里：它与笔记是 to-many，并进分页查询会让 {@code LIMIT} 切在 JOIN 之后的行上
 * （一篇笔记有几个标签就占几个名额，一页 20 条实际只回来七八篇）。单独一条 {@code IN} 查询
 * 批量取回（复用 {@code NoteMapper#selectTagRefsByNoteIds}），由 Service 按 {@code noteId} 装配。
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
