package com.wlf.note.dto;

/**
 * 上传成功的出参。
 *
 * <p>只回一个 id：上传后前端要么跳到详情页，要么把新笔记插进「我的上传」列表，
 * 两条路都只需要知道新建笔记的 id。
 */
public record NoteCreatedResponse(Long id) {
}
