package com.wlf.note.domain.note;

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
     * 1=对象已物理清理。
     */
    private Integer isPurged;

    private LocalDateTime createdAt;
}
