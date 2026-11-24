package com.wlf.user.application.result;

/** 跨服务用户展示信息。 */
public record UserDisplayResult(Long id, String nickname, String avatarKey, String collegeName) {
}
