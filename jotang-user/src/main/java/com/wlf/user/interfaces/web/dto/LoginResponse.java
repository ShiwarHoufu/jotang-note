package com.wlf.user.interfaces.web.dto;

/**
 * 登录出参：JWT + 用户信息。
 */
public record LoginResponse(String token, UserResponse user) {
}
