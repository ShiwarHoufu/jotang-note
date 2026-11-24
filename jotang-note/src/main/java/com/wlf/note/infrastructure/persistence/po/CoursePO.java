package com.wlf.note.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/** course 表映射。 */
@Getter
@Setter
@TableName("course")
public class CoursePO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;
    private Integer isOther;
}
