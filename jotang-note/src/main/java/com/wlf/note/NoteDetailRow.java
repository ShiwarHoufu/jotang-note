package com.wlf.note;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 详情主干查询的一行，{@link NoteMapper#selectDetail} 的返回类型。
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
