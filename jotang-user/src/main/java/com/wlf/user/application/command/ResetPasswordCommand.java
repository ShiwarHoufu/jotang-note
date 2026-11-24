package com.wlf.user.application.command;

/** 用令牌重置密码入参。 */
public record ResetPasswordCommand(String token, String newPassword) {
}
