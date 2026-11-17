package com.wlf.app;

import com.wlf.note.application.note.dto.UserProfile;
import com.wlf.note.application.port.out.UserProfileReader;
import com.wlf.user.application.profile.UserService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 测试夹具：单体测试仍用本地 {@link UserService} 读上传者资料。
 *
 * <p>运行时 user 已外提，由 {@code RemoteUserProfileReader} 走 Feign；测试用这个本地实现，
 * 避免为了保留 18 个 note 测试而全部改写成 stub。
 */
@Component
@ConditionalOnProperty(name = "jotang.remote.user.enabled", havingValue = "false")
public class LocalUserProfileReader implements UserProfileReader {

    private final UserService userService;

    public LocalUserProfileReader(UserService userService) {
        this.userService = userService;
    }

    @Override
    public Map<Long, UserProfile> batchGet(Collection<Long> userIds) {
        return userService.batchDisplayInfo(userIds).entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        entry -> new UserProfile(entry.getValue().id(),
                                entry.getValue().nickname(),
                                entry.getValue().avatarKey(),
                                entry.getValue().collegeName())));
    }
}
