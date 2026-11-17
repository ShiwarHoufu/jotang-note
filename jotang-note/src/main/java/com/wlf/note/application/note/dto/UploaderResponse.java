package com.wlf.note.application.note.dto;

/**
 * 笔记的上传者，详情与列表共用。
 *
 * @param avatarUrl   拼好的完整公网 URL
 * @param collegeName 上传者的学院
 */
public record UploaderResponse(Long id, String nickname, String avatarUrl, String collegeName) {
}
