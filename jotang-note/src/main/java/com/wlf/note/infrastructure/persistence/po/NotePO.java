package com.wlf.note.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** note 表映射。 */
@Getter
@Setter
@TableName("note")
public class NotePO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long uploaderId;
    private Long courseId;
    private String title;
    private String summary;
    private String teacher;
    private String status;
    private Long viewCount;
    private Long downloadCount;
    private Long favoriteCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;
}
