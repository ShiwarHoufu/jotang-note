package com.wlf.user.application.command;

/** 上传头像入参。 */
public record UploadAvatarCommand(Long userId, UploadedFile file) {
}
