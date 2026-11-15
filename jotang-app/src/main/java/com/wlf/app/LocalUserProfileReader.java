package com.wlf.app;

import com.wlf.note.application.note.dto.UserProfile;
import com.wlf.note.application.port.out.UserProfileReader;
import com.wlf.user.application.profile.UserService;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 单体期的本地适配器：把 note 的上传者端口转给 user-service。
 */
@Component
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
