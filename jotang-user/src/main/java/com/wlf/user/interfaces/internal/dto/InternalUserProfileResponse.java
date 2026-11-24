package com.wlf.user.interfaces.internal.dto;

/**
 * 服务间批量取用户展示信息。
 */
public record InternalUserProfileResponse(Long id, String nickname, String avatarKey, String collegeName) {
}
