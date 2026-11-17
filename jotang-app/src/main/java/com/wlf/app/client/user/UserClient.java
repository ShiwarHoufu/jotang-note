package com.wlf.app.client.user;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * user-service 内部用户资料接口。
 */
@FeignClient(name = "user-service",
        path = "/internal/users",
        configuration = UserClientConfig.class)
public interface UserClient {

    @GetMapping("/batch")
    List<InternalUserProfile> batch(@RequestParam("ids") List<Long> ids);
}
