package com.wlf.note.infrastructure.client.user;

import com.wlf.common.IdentityHeaders;
import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 内部调用的服务令牌。
 */
@Configuration(proxyBeanMethods = false)
public class UserClientConfig {

    @Bean
    public RequestInterceptor internalTokenInterceptor(
            @Value("${jotang.internal.token:dev-internal-token}") String token) {
        return template -> template.header(IdentityHeaders.INTERNAL_TOKEN, token);
    }
}
