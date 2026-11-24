package com.wlf.note.domain.note.query;

import java.util.List;

/** 详情缓存载荷；{@code detail == null} 表示负缓存。 */
public record NoteDetailView(NoteDetailSnapshot detail, List<NoteTagRefSnapshot> tags) {
}
