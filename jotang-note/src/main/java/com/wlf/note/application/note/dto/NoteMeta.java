package com.wlf.note.application.note.dto;

import com.wlf.note.application.catalog.dto.TagResponse;

import java.util.List;

/**
 * 笔记详情的缓存载荷：把七表 join 出来的主干与标签装成一个整体，共用一个 key。
 */
public record NoteMeta(NoteDetailRow row, List<TagResponse> tags) {
}
