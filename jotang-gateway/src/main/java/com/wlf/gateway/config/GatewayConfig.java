package com.wlf.gateway.config;

import com.wlf.common.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * 网关只扫 {@code com.wlf.gateway}，common 里带 {@code @Component} 的 JWT 校验器不会自动加载，这里显式装配。
 */
@Configuration(proxyBeanMethods = false)
public class GatewayConfig {

    @Bean
    public JwtTokenProvider jwtTokenProvider(@Value("${jwt.secret}") String secret,
                                             @Value("${jwt.ttl:2h}") Duration ttl) {
        return new JwtTokenProvider(secret, ttl);
    }
}
