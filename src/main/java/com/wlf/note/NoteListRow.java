package com.wlf.note;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 列表主干查询的一行，{@link NoteMapper#selectListPage} 的返回类型。
 *
 * <p><b>为什么不复用 {@link NoteDetailRow}</b>：两者的列不完全重合，而且这是两条不同的 SQL。
 * 列表不需要 {@code note_file} 的任何列（卡片不显示文件类型与大小），也不需要
 * {@code status}（本查询只出 ONLINE）、{@code summary} / {@code teacher}（卡片不展示）、
 * {@code created_at}（卡片显示的是「最后更新于」）。用详情那个投影接列表结果，
 * 会有一半字段恒为 null，而类名又写着 Detail——复用在这里只会让人猜「到底哪几个真有用」。
 *
 * <p>这不算「同一规则写了两份」：投影类是某一条 SQL 的形状，不是某条业务规则的副本。
 * 真正共享的东西（{@code course} / {@code tags} / 上传者的形状）已经复用
 * {@code CourseResponse} / {@code TagResponse} / {@code UploaderResponse} 了。
 *
 * <p>字段名与列的对应靠 MyBatis-Plus 默认开启的 {@code mapUnderscoreToCamelCase}，
 * 重名列（{@code id} / {@code name}）必须在 SQL 里显式起别名。
 *
 * <p>标签不在这里：它与笔记是 to-many，并进分页查询会乘行（一页 20 条会变成十几条）。
 * 单独一条 {@code IN} 查询批量取回，由 Service 按 {@code noteId} 装配。
 */
@Getter
@Setter
public class NoteListRow {

    // ---- note ----
    private Long id;
    private String title;
    private Long viewCount;
    private Long downloadCount;
    private Long favoriteCount;

    /** 用户最后编辑的时间，同时是 {@code sort=latest} 的排序键（§3.3） */
    private LocalDateTime updatedAt;

    // ---- course ----
    private Long courseId;
    private String courseName;

    // ---- user（上传者）----
    private Long uploaderId;
    private String nickname;

    /** 头像的 OSS 对象键，不是 URL；由 Service 拼成公网地址（§6.7） */
    private String avatar;

    // ---- college（上传者的学院，D8 推导而来）----
    private String collegeName;
}
