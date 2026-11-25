package com.wlf.note.infrastructure.cache;

import com.wlf.note.domain.uploader.UploaderProfile;
import com.wlf.note.domain.uploader.UploaderProfileCache;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 上传者资料缓存的 Redis 实现：一个 userId 一个 key。 */
@Repository
public class RedisUploaderProfileCache implements UploaderProfileCache {

    private static final String PREFIX = "note:uploader:";

    private final RedisTemplate<String, Object> redisTemplate;

    public RedisUploaderProfileCache(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Map<Long, UploaderProfile> getAll(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = List.copyOf(userIds);
        List<Object> values = redisTemplate.opsForValue()
                .multiGet(ids.stream().map(id -> PREFIX + id).toList());
        if (values == null) {
            return Map.of();
        }
        Map<Long, UploaderProfile> profiles = new HashMap<>(ids.size());
        for (int i = 0; i < ids.size(); i++) {
            if (values.get(i) instanceof UploaderProfile profile) {
                profiles.put(ids.get(i), profile);
            }
        }
        return profiles;
    }

    @Override
    public void putAll(Map<Long, UploaderProfile> profiles, Duration ttl) {
        profiles.forEach((id, profile) ->
                redisTemplate.opsForValue().set(PREFIX + id, profile, ttl));
    }
}
