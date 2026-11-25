package com.wlf.note.infrastructure.cache;

import com.wlf.note.domain.note.ViewCountBuffer;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 浏览量计数的 Redis 实现：HASH 存总量，SET 存待回写 id。 */
@Repository
public class RedisViewCountBuffer implements ViewCountBuffer {

    private static final String COUNTERS = "note:view:counters";
    private static final String DIRTY = "note:view:dirty";

    private static final DefaultRedisScript<Long> INCREMENT = new DefaultRedisScript<>("""
            if redis.call('HEXISTS', KEYS[1], ARGV[1]) == 0 then
                return -1
            end
            local total = redis.call('HINCRBY', KEYS[1], ARGV[1], 1)
            redis.call('SADD', KEYS[2], ARGV[1])
            return total
            """, Long.class);

    private static final DefaultRedisScript<Long> CLEAR_IF_UNCHANGED = new DefaultRedisScript<>("""
            local current = redis.call('HGET', KEYS[1], ARGV[1])
            if not current or tonumber(current) == tonumber(ARGV[2]) then
                redis.call('SREM', KEYS[2], ARGV[1])
                return 1
            end
            return 0
            """, Long.class);

    private static final DefaultRedisScript<Long> DISCARD_MISSING = new DefaultRedisScript<>("""
            for i = 1, #ARGV do
                if redis.call('HEXISTS', KEYS[1], ARGV[i]) == 0 then
                    redis.call('SREM', KEYS[2], ARGV[i])
                end
            end
            return 1
            """, Long.class);

    private final StringRedisTemplate redisTemplate;

    public RedisViewCountBuffer(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Long increment(Long noteId) {
        Long total = redisTemplate.execute(INCREMENT, List.of(COUNTERS, DIRTY), noteId.toString());
        return total == null || total < 0 ? null : total;
    }

    @Override
    public void initializeIfAbsent(Long noteId, long base) {
        redisTemplate.opsForHash().putIfAbsent(COUNTERS, noteId.toString(), Long.toString(base));
    }

    @Override
    public List<Long> dirtyNoteIds(int limit) {
        List<Long> ids = new ArrayList<>(limit);
        ScanOptions options = ScanOptions.scanOptions().count(limit).build();
        try (Cursor<String> cursor = redisTemplate.opsForSet().scan(DIRTY, options)) {
            while (cursor.hasNext() && ids.size() < limit) {
                ids.add(Long.valueOf(cursor.next()));
            }
        }
        return ids;
    }

    @Override
    public Map<Long, Long> totals(Collection<Long> noteIds) {
        if (noteIds == null || noteIds.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = List.copyOf(noteIds);
        HashOperations<String, String, String> hash = redisTemplate.opsForHash();
        List<String> values = hash.multiGet(COUNTERS, ids.stream().map(String::valueOf).toList());
        Map<Long, Long> totals = new HashMap<>(ids.size());
        for (int i = 0; i < ids.size(); i++) {
            String value = values.get(i);
            if (value != null) {
                totals.put(ids.get(i), Long.valueOf(value));
            }
        }
        return totals;
    }

    @Override
    public boolean clearIfUnchanged(Long noteId, long writtenTotal) {
        Long cleared = redisTemplate.execute(CLEAR_IF_UNCHANGED,
                List.of(COUNTERS, DIRTY), noteId.toString(), Long.toString(writtenTotal));
        return cleared != null && cleared == 1L;
    }

    @Override
    public void discard(Collection<Long> noteIds) {
        if (noteIds == null || noteIds.isEmpty()) {
            return;
        }
        Object[] ids = noteIds.stream().map(String::valueOf).toArray();
        redisTemplate.execute(DISCARD_MISSING, List.of(COUNTERS, DIRTY), ids);
    }
}
