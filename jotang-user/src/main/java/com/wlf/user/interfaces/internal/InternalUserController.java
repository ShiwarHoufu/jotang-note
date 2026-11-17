package com.wlf.user.interfaces.internal;

import com.wlf.user.application.dto.UserDisplayInfo;
import com.wlf.user.application.profile.UserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;

/**
 * 供 note-service 调用的内部接口。网关不暴露，由服务令牌保护。
 */
@RestController
@RequestMapping("/internal/users")
public class InternalUserController {

    private final UserService userService;

    public InternalUserController(UserService userService) {
        this.userService = userService;
    }

    /** 不存在的 id 不出现在结果里，与消费方端口约定一致。 */
    @GetMapping("/batch")
    public List<InternalUserProfileResponse> batch(@RequestParam("ids") List<Long> ids) {
        return userService.batchDisplayInfo(ids).values().stream()
                .sorted(Comparator.comparing(UserDisplayInfo::id))
                .map(user -> new InternalUserProfileResponse(
                        user.id(), user.nickname(), user.avatarKey(), user.collegeName()))
                .toList();
    }
}
