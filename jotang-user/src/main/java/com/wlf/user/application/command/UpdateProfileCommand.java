package com.wlf.user.application.command;

/** 修改用户资料入参。 */
public record UpdateProfileCommand(Long userId, String nickname, Long collegeId) {
}
