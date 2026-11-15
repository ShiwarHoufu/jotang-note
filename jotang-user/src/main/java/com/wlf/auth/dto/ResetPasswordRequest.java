package com.wlf.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 重置密码入参：邮件令牌 / 新密码。
 *
 * <p>{@code token} 不做格式校验（长度、字符集都不查）：它的合法性只由
 * {@code PasswordResetTokenMapper#consume} 那条条件 UPDATE 判定
 */
@Getter
@Setter
public class ResetPasswordRequest {

    @NotBlank(message = "令牌不能为空")
    private String token;

    @NotBlank(message = "密码不能为空")
    @Size(min = 8, max = 64, message = "密码长度需为 8~64 个字符")
    private String newPassword;
}
