package com.wlf.app.client.user;

/**
 * user-service 内部接口的响应形状（消费方自有 DTO）。
 */
public record InternalUserProfile(Long id, String nickname, String avatarKey, String collegeName) {
}
