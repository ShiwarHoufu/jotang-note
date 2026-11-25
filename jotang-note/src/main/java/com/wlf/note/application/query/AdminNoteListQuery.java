package com.wlf.note.application.query;

/** 管理员「已下架笔记」列表查询。 */
public record AdminNoteListQuery(int page, int size, String keyword) {
}
