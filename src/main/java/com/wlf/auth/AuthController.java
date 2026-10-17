package com.wlf.auth;

import com.wlf.auth.dto.ForgotPasswordRequest;
import com.wlf.auth.dto.LoginRequest;
import com.wlf.auth.dto.LoginResponse;
import com.wlf.auth.dto.RegisterRequest;
import com.wlf.auth.dto.ResetPasswordRequest;
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
 *
 * <p>注册、登录与密码找回的两个接口在 SecurityConfig 的放行名单里，其余接口需登录。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final PasswordResetService passwordResetService;

    public AuthController(AuthService authService, PasswordResetService passwordResetService) {
        this.authService = authService;
        this.passwordResetService = passwordResetService;
    }

    @PostMapping("/register")
    public ApiResponse<UserInfo> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.ok(authService.register(request));
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }



    /*
     @AuthenticationPrincipal 是 Spring Security 提供的参数注解
     作用是：在 Controller 方法参数上，直接把`SecurityContext` 里存的那份“身份主体”（Principal）取出来注入
     */
    @GetMapping("/me")
    public ApiResponse<UserInfo> me(@AuthenticationPrincipal AuthenticatedUser currentUser) {
        return ApiResponse.ok(authService.currentUser(currentUser.userId()));
    }

    /**
     * 退出。服务端无会话可销毁，前端删除本地 token 即为退出。
     *
     * <p>保留该接口是为了让前端有个统一的调用点，也便于 V2 引入 Redis 后
     * 用 jti 黑名单把语义补实。
     */
    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        return ApiResponse.ok();
    }

    /**
     * 申请重置密码。
     *
     * <p>无论邮箱是否注册过，返回的都是200。
     */
    @PostMapping("/password/forgot")
    public ApiResponse<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        passwordResetService.forgot(request.getEmail());
        return ApiResponse.ok();
    }

    /**
     * 用邮件里的令牌设置新密码。
     *
     * <p>身份由令牌本身证明。
     */
    @PostMapping("/password/reset")
    public ApiResponse<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.reset(request.getToken(), request.getNewPassword());
        return ApiResponse.ok();
    }
}
