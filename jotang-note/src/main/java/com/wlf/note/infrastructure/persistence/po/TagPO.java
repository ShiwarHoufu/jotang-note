package com.wlf.note.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/** tag 表映射。 */
@Getter
@Setter
@TableName("tag")
public class TagPO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;
}
