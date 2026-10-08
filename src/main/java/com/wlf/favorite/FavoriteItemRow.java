package com.wlf.favorite;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 「我的收藏」主干查询的一行，{@link FavoriteMapper#selectFavoritePage} 的返回类型。
 *
 * <p>字段名与列的对应靠 MyBatis-Plus 默认开启的 {@code mapUnderscoreToCamelCase}，
 * 重名列（{@code id} / {@code name}）与语义变了名的列（{@code f.created_at} 要读成收藏时间）
 * 必须在 SQL 里显式起别名——{@code f.created_at} 直接回填会落进 {@code createdAt}，
 * 与 {@code n.updated_at} 在概念上打架。
 *
 * <p><b>为什么不复用 {@code NoteListRow}</b>：两者的列不重合，而且这是两条不同的 SQL。
 * 这里要 {@code status}（列表接口只出 ONLINE，所以那边没有）、要 {@code f.created_at}（收藏时间），
 * 不要 {@code NoteListRow} 的 {@code updatedAt} 之外的排序键含义。投影类是某一条 SQL 的形状，
 * 不是某条业务规则的副本；真正共享的东西（{@code course} / {@code tags} / 上传者的形状）
 * 已经复用 {@code CourseResponse} / {@code TagResponse} / {@code UploaderResponse} 了。
 *
 * <p><b>用 setter 而不是 record</b>：位置绑定的 record 在相邻两列同为 {@code Long} 时
 * 一旦顺序写反就会静默接错，而 {@code viewCount} / {@code downloadCount} / {@code favoriteCount}
 * 正是三个紧挨着的 {@code Long}。理由同 {@code NoteTagRef}。
 *
 * <p>标签不在这里：它与笔记是 to-many，并进分页查询会让 LIMIT 切在 JOIN 之后的行上
 * （一篇笔记有几个标签就占几个名额）。单独一条 {@code IN} 查询批量取回，由 Service 装配。
 */
@Getter
@Setter
public class FavoriteItemRow {

    /** 被收藏的笔记 id。注意不是 {@code favorite.id}——对外只谈「哪篇笔记」，不暴露关系行本身 */
    private Long noteId;

    /** 收藏时刻，同时是本查询的排序键。列名 {@code f.created_at} 在 SQL 里已别名为 {@code favorited_at} */
    private LocalDateTime favoritedAt;

    /** {@code ONLINE} / {@code OFFLINE} / {@code DELETED}。三种都要取回来，由 Service 决定正常项还是占位项 */
    private String status;

    private String title;

    // ---- course ----
    private Long courseId;
    private String courseName;

    // ---- user（上传者）。占位项不对外暴露 ----
    private Long uploaderId;
    private String nickname;

    /** 头像的 OSS 对象键，不是 URL；由 Service 拼成公网地址（§6.7） */
    private String avatar;

    // ---- college（上传者的学院，D8 推导而来）----
    private String collegeName;

    // ---- 计数与编辑时间。占位项一律不对外暴露 ----
    private Long viewCount;
    private Long downloadCount;
    private Long favoriteCount;

    /** 笔记最后被编辑的时间。与 {@link #favoritedAt} 是两回事，别读混 */
    private LocalDateTime updatedAt;
}
