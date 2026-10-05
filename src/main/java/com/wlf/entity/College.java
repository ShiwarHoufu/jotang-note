package com.wlf.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/**
 * college 表。预置数据，用户注册时必选，用户不能自建。
 * 见《概要设计》§3.2、决策 D8。
 *
 * <p>学院挂在用户而不是课程上：笔记的学院归属由上传者推导，
 * 因此这里只有 id 与名称，没有任何统计字段。
 */
@Getter
@Setter
@TableName("college")
public class College {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;
}
