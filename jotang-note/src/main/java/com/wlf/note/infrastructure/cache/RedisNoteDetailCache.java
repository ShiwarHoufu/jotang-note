package com.wlf.note.infrastructure.cache;

import com.wlf.note.domain.note.NoteDetailCache;
import com.wlf.note.domain.note.query.NoteDetailView;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;

/** 详情缓存的 Redis 实现。 */
@Repository
public class RedisNoteDetailCache implements NoteDetailCache {

    private static final String PREFIX = "note:meta:";

    private final RedisTemplate<String, Object> redisTemplate;

    public RedisNoteDetailCache(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public NoteDetailView get(Long noteId) {
        Object value = redisTemplate.opsForValue().get(PREFIX + noteId);
        return value instanceof NoteDetailView view ? view : null;
    }

    @Override
    public void put(Long noteId, NoteDetailView view, Duration ttl) {
        redisTemplate.opsForValue().set(PREFIX + noteId, view, ttl);
    }

    @Override
    public void evict(Long noteId) {
        redisTemplate.delete(PREFIX + noteId);
    }
}
