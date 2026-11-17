package com.wlf.ai.config;

import com.wlf.common.feign.UserContextFeignInterceptor;
import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 全局 Feign 拦截器：把当前用户身份透传给被调用服务。
 */
@Configuration(proxyBeanMethods = false)
public class FeignConfig {

    @Bean
    public RequestInterceptor userContextFeignInterceptor() {
        return new UserContextFeignInterceptor();
    }
}
