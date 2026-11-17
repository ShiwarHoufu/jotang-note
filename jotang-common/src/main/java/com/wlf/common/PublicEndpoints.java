package com.wlf.common;

/**
 * 免登录接口清单：网关放行与各服务 SecurityConfig 共用，避免两处名单漂移。
 */
public final class PublicEndpoints {

    public static final String[] PATHS = {
            "/api/auth/register",
            "/api/auth/login",
            "/api/auth/password/forgot",
            "/api/auth/password/reset",
            "/api/colleges" // 注册页要拿它填学院下拉框
    };

    private PublicEndpoints() {
    }
}
