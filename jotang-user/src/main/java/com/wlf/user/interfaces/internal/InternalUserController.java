package com.wlf.user.interfaces.internal;

import com.wlf.user.application.service.UserService;
import com.wlf.user.application.result.UserDisplayResult;
import com.wlf.user.interfaces.internal.assembler.InternalUserAssembler;
import com.wlf.user.interfaces.internal.dto.InternalUserProfileResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 供 note-service 调用的内部接口。网关不暴露，由服务令牌保护。
 */
@RestController
@RequestMapping("/internal/users")
public class InternalUserController {

    private final UserService userService;
    private final InternalUserAssembler assembler;

    public InternalUserController(UserService userService, InternalUserAssembler assembler) {
        this.userService = userService;
        this.assembler = assembler;
    }

    /** 不存在的 id 不出现在结果里，与消费方端口约定一致。 */
    @GetMapping("/batch")
    public List<InternalUserProfileResponse> batch(@RequestParam("ids") List<Long> ids) {
        List<UserDisplayResult> results = userService.batchDisplay(assembler.toQuery(ids));
        return results.stream()
                .map(assembler::toResponse)
                .toList();
    }
}
