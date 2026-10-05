package com.wlf.auth;

import com.wlf.auth.dto.LoginRequest;
import com.wlf.auth.dto.LoginResponse;
import com.wlf.auth.dto.RegisterRequest;
import com.wlf.auth.dto.UserInfo;
import com.wlf.common.ApiResponse;
import com.wlf.common.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口：注册 / 登录 / 退出 / 当前用户 / 密码找回。
 * 见《概要设计》§5.1。
 *
 * <p>注册与登录在 SecurityConfig 的放行名单里，其余接口需登录。
 *
 * <p>密码找回（forgot / reset）待独立一步实现，涉及 SMTP 与 password_reset_token 表。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ApiResponse<UserInfo> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.ok(authService.register(request));
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }


    /**
     * `@AuthenticationPrincipal` 是 Spring Security 提供的参数注解
     * 作用是： 在 Controller 方法参数上，直接把`SecurityContext` 里存的那份“身份主体”（Principal）取出来注入
     */
    @GetMapping("/me")
    public ApiResponse<UserInfo> me(@AuthenticationPrincipal AuthenticatedUser currentUser) {
        return ApiResponse.ok(authService.currentUser(currentUser.userId()));
    }

    /**
     * 退出。服务端无会话可销毁，前端删除本地 token 即为退出（§6.4、决策 D1）。
     *
     * <p>保留该接口是为了让前端有个统一的调用点，也便于 V2 引入 Redis 后
     * 用 jti 黑名单把语义补实。
     */
    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        return ApiResponse.ok();
    }
}
