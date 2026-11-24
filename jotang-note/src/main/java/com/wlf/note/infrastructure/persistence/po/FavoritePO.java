package com.wlf.note.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** favorite 表映射。 */
@Getter
@Setter
@TableName("favorite")
public class FavoritePO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private Long noteId;
    private LocalDateTime createdAt;
}
