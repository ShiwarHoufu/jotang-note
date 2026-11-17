package com.wlf.user.interfaces.internal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 把用户服务的内部接口挂到服务令牌拦截器下。
 */
@Configuration("userInternalApiConfig")
public class InternalApiConfig implements WebMvcConfigurer {

    private final String token;

    public InternalApiConfig(@Value("${jotang.internal.token:dev-internal-token}") String token) {
        this.token = token;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new InternalTokenInterceptor(token))
                .addPathPatterns("/internal/users/**");
    }
}
