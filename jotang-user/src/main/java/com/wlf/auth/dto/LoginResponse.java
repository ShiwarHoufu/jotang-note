package com.wlf.auth.dto;

/**
 * 登录出参：JWT + 用户信息。见《概要设计》§5.1。
 *
 * <p>前端把 token 存 Pinia + localStorage，由 Axios 拦截器注入
 * {@code Authorization: Bearer <token>}（§6.4）。
 */
public record LoginResponse(String token, UserInfo user) {
}
