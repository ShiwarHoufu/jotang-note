package com.wlf.note.application.note.dto;

/**
 * 笔记列表与详情需要的上传者展示信息。
 */
public record UserProfile(Long id, String nickname, String avatarKey, String collegeName) {
}
