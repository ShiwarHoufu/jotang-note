package com.wlf.user.application.command;

/** 注册用例入参。 */
public record RegisterCommand(String username, String email, String password, Long collegeId) {
}
