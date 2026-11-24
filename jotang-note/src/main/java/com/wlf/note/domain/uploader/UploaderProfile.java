package com.wlf.note.domain.uploader;

/** 上传者的展示信息，跨服务只读投影。 */
public record UploaderProfile(Long id, String nickname, String avatarKey, String collegeName) {
}
