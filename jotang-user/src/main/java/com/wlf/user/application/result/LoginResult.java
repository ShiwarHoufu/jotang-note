package com.wlf.user.application.result;

/** 登录用例出参：JWT + 用户信息。 */
public record LoginResult(String token, UserResult user) {
}
