package com.wlf.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * user 表。角色 USER / ADMIN，密码 BCrypt 存储。
 * 见《概要设计》§3.2。
 * 表名加反引号：{@code user} 在 MySQL 中是关键字，避免拼 SQL 时被当作函数名。
 */
@Getter
@Setter
@TableName("`user`")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private String email;

    /** BCrypt 哈希，绝不外传 */
    private String passwordHash;

    private String nickname;

    /**
     * 所属学院，注册时必选（D8）。外键指向 {@code college.id}，
     * 因此该表的外键异常不等于数据损坏——见 AuthService 里对入参的先校验。
     */
    private Long collegeId;

    /** OSS 对象键（非完整 URL）；对外拼公网 URL 见 §6.7 */
    private String avatar;
    /** USER / ADMIN */
    private String role;
    /** 1 正常 0 禁用（预留） */
    private Integer status;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
