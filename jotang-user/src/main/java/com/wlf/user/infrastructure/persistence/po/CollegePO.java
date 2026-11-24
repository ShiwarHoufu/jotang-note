package com.wlf.user.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/** college 表的持久化对象。 */
@Getter
@Setter
@TableName("college")
public class CollegePO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
}
