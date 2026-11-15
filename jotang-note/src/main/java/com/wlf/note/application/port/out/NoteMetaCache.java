package com.wlf.note.application.port.out;

import com.wlf.note.application.note.dto.NoteMeta;

import java.time.Duration;

/**
 * 笔记详情缓存的出站端口。
 */
public interface NoteMetaCache {

    /** 未命中返回 null；负缓存返回 row 为 null 的 {@link NoteMeta}。 */
    NoteMeta get(Long noteId);

    void put(Long noteId, NoteMeta meta, Duration ttl);

    void evict(Long noteId);
}
