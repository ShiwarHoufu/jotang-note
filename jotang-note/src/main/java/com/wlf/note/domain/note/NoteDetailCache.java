package com.wlf.note.domain.note;

import com.wlf.note.domain.note.query.NoteDetailView;

import java.time.Duration;

/** 详情缓存端口。未命中返回 null；负缓存返回 detail 为 null 的视图。 */
public interface NoteDetailCache {

    NoteDetailView get(Long noteId);

    void put(Long noteId, NoteDetailView view, Duration ttl);

    void evict(Long noteId);
}
