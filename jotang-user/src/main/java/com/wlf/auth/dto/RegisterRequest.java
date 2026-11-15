package com.wlf.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 注册入参：用户名 / 邮箱 / 密码 / 学院。
 *
 * <p>不含昵称——昵称由注册时的用户名初始化（user.nickname 为 NOT NULL），
 * 之后在基本资料里单独修改。
 */
@Getter
@Setter
public class RegisterRequest {

    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 32, message = "用户名长度需为 3~32 个字符")
    @Pattern(regexp = "^[A-Za-z0-9_]+$", message = "用户名只能包含字母、数字和下划线")
    private String username;

    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    @Size(max = 128, message = "邮箱长度不能超过 128 个字符")
    private String email;

    @NotBlank(message = "密码不能为空")
    @Size(min = 8, max = 64, message = "密码长度需为 8~64 个字符")
    private String password;

    /** 只校验非空；「这个 id 是否真实存在」由 AuthService 查库判断（§5.1、D8） */
    @NotNull(message = "学院不能为空")
    private Long collegeId;
}
