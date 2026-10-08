package com.wlf.note;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 详情主干查询的一行，{@link NoteMapper#selectDetail} 的返回类型。
 *
 * <p>为什么是这么一个「把五张表拍平」的载体，而不是直接映射成 {@link com.wlf.note.dto.NoteDetailResponse}：
 * 后者是 record，MyBatis 回填不了（要构造器注入而不是 setter）。而且这两件事本来就该分开——
 * 本类是<b>查询投影</b>，如实反映 SQL 捞回来的东西；响应体是**经过策略裁剪**之后的样子
 * （非 ONLINE 时文件字段被置空、{@code previewMode} 由 {@code contentType} 现推、
 * 学院被挪进 uploader 里）。让 SQL 直接产出响应体，等于把策略写进 mapper。
 *
 * <p>字段名与列的对应靠 MyBatis-Plus 默认开启的 {@code mapUnderscoreToCamelCase}：
 * {@code original_name} → {@code originalName}，{@code course_id} → {@code courseId}。
 * 五张表都有 {@code id}、{@code name} 这类重名列，所以 SQL 里的别名不是可选的——
 * {@code c.id} 与 {@code u.id} 都必须显式改名，否则后出现的那个会覆盖前面的。
 *
 * <p>文件那几个字段（{@code originalName} / {@code size} / {@code contentType}）
 * <b>无论笔记是什么状态都会被查出来</b>，是否对外暴露由 {@link NoteService#detail} 按
 * {@code status} 决定。这是有意的分工：SQL 负责陈述事实，策略留在 Service 里可读可测。
 */
@Getter
@Setter
public class NoteDetailRow {

    // ---- note ----
    private Long id;
    private String title;
    private String summary;
    private String teacher;

    /** {@link NoteStatus} 的 name()，由 Service 转成枚举 */
    private String status;

    private Long viewCount;
    private Long downloadCount;
    private Long favoriteCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // ---- note_file（非 ONLINE 时 Service 会丢弃这三个）----
    private String originalName;
    private Long size;
    private String contentType;

    // ---- course ----
    private Long courseId;
    private String courseName;

    // ---- user（上传者）----
    private Long uploaderId;
    private String nickname;

    /** 头像的 OSS 对象键，不是 URL；由 Service 经 storageService 拼成公网地址（§6.7） */
    private String avatar;

    // ---- college（上传者的学院，D8 推导而来）----
    private String collegeName;
}
