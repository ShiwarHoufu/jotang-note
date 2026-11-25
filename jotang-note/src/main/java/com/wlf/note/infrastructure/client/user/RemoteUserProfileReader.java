package com.wlf.note.infrastructure.client.user;

import com.wlf.note.domain.uploader.UploaderProfile;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * note 侧读上传者资料的 Feign 适配器。
 *
 * <p>只是 {@link CachingUploaderProfileReader} 的协作者，不直接实现端口；异常直接抛出。
 */
@Component
@ConditionalOnProperty(name = "jotang.remote.user.enabled", havingValue = "true", matchIfMissing = true)
public class RemoteUserProfileReader {

    private final UserClient userClient;

    public RemoteUserProfileReader(UserClient userClient) {
        this.userClient = userClient;
    }

    public Map<Long, UploaderProfile> fetch(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = userIds.stream().distinct().toList();
        return userClient.batch(ids).stream()
                .collect(Collectors.toMap(
                        InternalUserProfile::id,
                        profile -> new UploaderProfile(profile.id(), profile.nickname(),
                                profile.avatarKey(), profile.collegeName())));
    }
}
