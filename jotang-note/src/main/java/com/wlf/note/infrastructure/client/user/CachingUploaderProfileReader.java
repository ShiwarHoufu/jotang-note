package com.wlf.note.infrastructure.client.user;

import com.wlf.note.domain.uploader.UploaderProfile;
import com.wlf.note.domain.uploader.UploaderProfileCache;
import com.wlf.note.domain.uploader.UploaderProfileReader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 上传者资料的缓存装饰器：先查 Redis，缺失的 id 才批量回源。
 *
 * <p>缓存故障只降级、不影响请求；远端失败时返回已命中部分，且不回填缓存。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "jotang.remote.user.enabled", havingValue = "true", matchIfMissing = true)
public class CachingUploaderProfileReader implements UploaderProfileReader {

    private final RemoteUserProfileReader remote;
    private final UploaderProfileCache cache;
    private final Duration ttl;

    public CachingUploaderProfileReader(RemoteUserProfileReader remote,
                                    UploaderProfileCache cache,
                                    @Value("${jotang.uploader.cache.ttl:10m}") Duration ttl) {
        this.remote = remote;
        this.cache = cache;
        this.ttl = ttl;
    }

    @Override
    public Map<Long, UploaderProfile> batchGet(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = userIds.stream().distinct().toList();

        Map<Long, UploaderProfile> cached = readCache(ids);
        List<Long> missing = ids.stream().filter(id -> !cached.containsKey(id)).toList();
        if (missing.isEmpty()) {
            return cached;
        }

        Map<Long, UploaderProfile> fetched;
        try {
            fetched = remote.fetch(missing);
        } catch (RuntimeException ex) {
            log.warn("读取用户资料失败，仅使用已缓存部分：{}", ex.getMessage());
            return cached;
        }
        writeCache(fetched);

        Map<Long, UploaderProfile> merged = new HashMap<>(cached);
        merged.putAll(fetched);
        return merged;
    }

    private Map<Long, UploaderProfile> readCache(List<Long> ids) {
        try {
            return cache.getAll(ids);
        } catch (RuntimeException ex) {
            log.warn("读取上传者资料缓存失败，回源查询：{}", ex.getMessage());
            return Map.of();
        }
    }

    private void writeCache(Map<Long, UploaderProfile> profiles) {
        if (profiles.isEmpty()) {
            return;
        }
        try {
            cache.putAll(profiles, ttl);
        } catch (RuntimeException ex) {
            log.warn("回填上传者资料缓存失败：{}", ex.getMessage());
        }
    }
}
