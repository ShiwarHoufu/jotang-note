package com.wlf.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * note 表。status 为 ONLINE / OFFLINE / DELETED（{@link com.wlf.note.NoteStatus}），
 * DELETED 是软删除，行不物理删除。
 * 见《概要设计》§3.2、§4.1。
 *
 * <p>笔记的学院归属不在这里存——学院挂在 {@code user.college_id} 上，
 * 读取时 JOIN 上传者推导（决策 D8，§3.3）。
 */
@Getter
@Setter
@TableName("note")
public class Note {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long uploaderId;
    private Long courseId;

    private String title;

    /** 简介，存原文，输出时转义（§3 统一约定） */
    private String summary;

    private String teacher;

    /** {@link com.wlf.note.NoteStatus} 的 name()。迁移只能经 NoteStateMachine，禁止在此直接改 */
    private String status;

    // 三个计数列都是 INT UNSIGNED（上界 4294967295），超出 Integer.MAX_VALUE，
    // 故用 Long 承接。递增一律走 `SET x = x + 1` 的原子 UPDATE（§3.3），不做「读出来加一再写回」
    private Long viewCount;
    private Long downloadCount;
    private Long favoriteCount;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** status=DELETED 时写入 */
    private LocalDateTime deletedAt;
}
