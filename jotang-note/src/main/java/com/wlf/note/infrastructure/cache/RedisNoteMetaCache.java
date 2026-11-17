package com.wlf.note.infrastructure.cache;

import com.wlf.note.application.note.dto.NoteMeta;
import com.wlf.note.application.port.out.NoteMetaCache;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;

/**
 * 笔记详情缓存的 Redis 实现。
 */
@Repository
public class RedisNoteMetaCache implements NoteMetaCache {

    private static final String PREFIX = "note:meta:";

    private final RedisTemplate<String, Object> redisTemplate;

    public RedisNoteMetaCache(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public NoteMeta get(Long noteId) {
        Object value = redisTemplate.opsForValue().get(PREFIX + noteId);
        return value instanceof NoteMeta meta ? meta : null;
    }

    @Override
    public void put(Long noteId, NoteMeta meta, Duration ttl) {
        redisTemplate.opsForValue().set(PREFIX + noteId, meta, ttl);
    }

    @Override
    public void evict(Long noteId) {
        redisTemplate.delete(PREFIX + noteId);
    }
}
