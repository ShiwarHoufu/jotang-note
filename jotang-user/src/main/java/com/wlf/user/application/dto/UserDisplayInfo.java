package com.wlf.user.application.dto;

/**
 * 跨服务展示用的用户摘要：只含昵称、头像键与学院名。
 */
public record UserDisplayInfo(Long id, String nickname, String avatarKey, String collegeName) {
}
