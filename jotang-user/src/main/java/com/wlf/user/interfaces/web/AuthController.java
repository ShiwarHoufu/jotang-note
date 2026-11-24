package com.wlf.user.interfaces.web;

import com.wlf.user.application.service.AuthService;
import com.wlf.user.application.service.PasswordResetService;
import com.wlf.user.application.result.LoginResult;
import com.wlf.user.application.result.UserResult;
import com.wlf.common.ApiResponse;
import com.wlf.common.AuthenticatedUser;
import com.wlf.user.interfaces.web.assembler.AuthAssembler;
import com.wlf.user.interfaces.web.assembler.PasswordResetAssembler;
import com.wlf.user.interfaces.web.assembler.UserAssembler;
import com.wlf.user.interfaces.web.dto.ForgotPasswordRequest;
import com.wlf.user.interfaces.web.dto.LoginRequest;
import com.wlf.user.interfaces.web.dto.LoginResponse;
import com.wlf.user.interfaces.web.dto.RegisterRequest;
import com.wlf.user.interfaces.web.dto.ResetPasswordRequest;
import com.wlf.user.interfaces.web.dto.UserResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口：注册 / 登录 / 退出 / 当前用户 / 密码找回。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final PasswordResetService passwordResetService;
    private final AuthAssembler authAssembler;
    private final PasswordResetAssembler passwordResetAssembler;
    private final UserAssembler userAssembler;

    public AuthController(AuthService authService,
                          PasswordResetService passwordResetService,
                          AuthAssembler authAssembler,
                          PasswordResetAssembler passwordResetAssembler,
                          UserAssembler userAssembler) {
        this.authService = authService;
        this.passwordResetService = passwordResetService;
        this.authAssembler = authAssembler;
        this.passwordResetAssembler = passwordResetAssembler;
        this.userAssembler = userAssembler;
    }

    @PostMapping("/register")
    public ApiResponse<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        UserResult result = authService.register(authAssembler.toCommand(request));
        return ApiResponse.ok(userAssembler.toResponse(result));
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResult result = authService.login(authAssembler.toCommand(request));
        return ApiResponse.ok(authAssembler.toLoginResponse(result));
    }

    @GetMapping("/me")
    public ApiResponse<UserResponse> me(@AuthenticationPrincipal AuthenticatedUser currentUser) {
        UserResult result = authService.currentUser(authAssembler.toQuery(currentUser.userId()));
        return ApiResponse.ok(userAssembler.toResponse(result));
    }

    /**
     * 退出。服务端无会话可销毁，前端删除本地 token 即为退出。
     *
     * <p>保留该接口是为了让前端有个统一的调用点，也便于引入 Redis 后用 jti 黑名单把语义补实。
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
        passwordResetService.forgot(passwordResetAssembler.toCommand(request));
        return ApiResponse.ok();
    }

    /**
     * 用邮件里的令牌设置新密码。
     *
     * <p>身份由令牌本身证明。
     */
    @PostMapping("/password/reset")
    public ApiResponse<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.reset(passwordResetAssembler.toCommand(request));
        return ApiResponse.ok();
    }
}
