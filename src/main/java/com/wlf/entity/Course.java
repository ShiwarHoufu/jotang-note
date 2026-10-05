package com.wlf.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * course 表。预置数据，is_other=1 为内置「其他」课程，用户不能自建。
 * 见《概要设计》§3.2。
 *
 * <p>不记开课学院（决策 D8）：学院挂在 {@link User} 的 collegeId 上，
 * 笔记的学院归属由上传者推导，见《概要设计》§3.3。
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@TableName("course")
public class Course {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;

    /**
     * 用 Integer 而非 boolean：TINYINT 列与 User.status 保持一致，也避开 Lombok
     * 对 boolean 字段生成 isOther() 后 Jackson 把属性名判成 other 的问题。
     */
    private Integer isOther;
}
