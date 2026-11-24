package com.wlf.note.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** note_file 表映射。 */
@Getter
@Setter
@TableName("note_file")
public class NoteFilePO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long noteId;
    private String storageKey;
    private String originalName;
    private Long size;
    private String contentType;
    private Integer isPurged;
    private LocalDateTime createdAt;
}
