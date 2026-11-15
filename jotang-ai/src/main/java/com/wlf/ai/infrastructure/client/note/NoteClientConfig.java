package com.wlf.ai.infrastructure.client.note;

import feign.RequestInterceptor;
import feign.codec.ErrorDecoder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

/**
 * 内部调用的服务令牌与错误翻译。
 */
@Configuration(proxyBeanMethods = false)
public class NoteClientConfig {

    @Bean
    public RequestInterceptor internalTokenInterceptor(
            @Value("${jotang.internal.token:dev-internal-token}") String token) {
        return template -> template.header("X-Internal-Token", token);
    }

    @Bean
    public ErrorDecoder internalErrorDecoder(ObjectMapper objectMapper) {
        return new NoteClientErrorDecoder(objectMapper);
    }
}
