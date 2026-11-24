package com.wlf.user.interfaces.web.dto;

/** 用户 HTTP 响应。 */
public record UserResponse(Long id, String username, String nickname, Long collegeId,
                           String avatarUrl, String role) {
}
