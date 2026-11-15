package com.wlf.note.domain.catalog;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/**
 * tag 表。data.sql 预置若干常用标签，用户上传时也可自由打标，与 note 多对多。
 * 见《概要设计》§3.2。
 */
@Getter
@Setter
@TableName("tag")
public class Tag {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;
}
