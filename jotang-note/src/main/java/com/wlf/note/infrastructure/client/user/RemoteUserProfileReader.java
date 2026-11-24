package com.wlf.note.infrastructure.client.user;

import com.wlf.note.domain.uploader.UploaderProfile;
import com.wlf.note.domain.uploader.UploaderProfileReader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * note 侧读上传者资料的 Feign 适配器。
 *
 * <p>user-service 不可用时降级为空 Map：列表还能出，上传者信息留空。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "jotang.remote.user.enabled", havingValue = "true", matchIfMissing = true)
public class RemoteUserProfileReader implements UploaderProfileReader {

    private final UserClient userClient;

    public RemoteUserProfileReader(UserClient userClient) {
        this.userClient = userClient;
    }

    @Override
    public Map<Long, UploaderProfile> batchGet(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = userIds.stream().distinct().toList();
        try {
            return userClient.batch(ids).stream()
                    .collect(Collectors.toMap(
                            InternalUserProfile::id,
                            profile -> new UploaderProfile(profile.id(), profile.nickname(),
                                    profile.avatarKey(), profile.collegeName())));
        } catch (RuntimeException ex) {
            log.warn("读取用户资料失败，上传者信息降级为空：{}", ex.getMessage());
            return Map.of();
        }
    }
}
