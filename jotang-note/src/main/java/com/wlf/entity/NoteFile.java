package com.wlf.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * note_file 表。一份笔记一个文件（{@code uk_note_id} 保证），
 * 独立成表是为 V2「一份笔记多个附件」留余地。
 * 见《概要设计》§3.2。
 */
@Getter
@Setter
@TableName("note_file")
public class NoteFile {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long noteId;

    /** OSS 对象键：{@code notes/yyyy/MM/{uuid}.{ext}}，由 NoteFilePolicy#objectKey 生成 */
    private String storageKey;

    /** 原始文件名，下载时用它还原（已清洗掉客户端可能送来的路径） */
    private String originalName;

    /** 列是 BIGINT UNSIGNED，必须用 Long */
    private Long size;

    /** 服务端判定的类型，上传时写入对象元数据，读取时不再覆写（§6.2） */
    private String contentType;

    /**
     * 1=对象已物理清理。与 {@link Course#getIsOther()} 同理用 Integer 而非 boolean：
     * TINYINT 列与 {@code user.status} 保持一致，也避开 Lombok 对 boolean 生成
     * {@code isPurged()} 后 Jackson 把属性名判成 {@code purged} 的问题。
     */
    private Integer isPurged;

    private LocalDateTime createdAt;
}
