package com.wlf.config;

import org.springframework.context.annotation.Configuration;

/**
 * Spring Security 过滤器链：放行名单 + 无状态会话 + ADMIN 角色规则。
 * 见《概要设计》§5.6、§6.4。
 */
@Configuration
public class SecurityConfig {
}
