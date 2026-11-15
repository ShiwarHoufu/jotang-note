package com.wlf.user.interfaces.web;

import com.wlf.user.application.dto.UserInfo;
import com.wlf.user.application.profile.UserService;
import com.wlf.common.ApiResponse;
import com.wlf.common.AuthenticatedUser;
import com.wlf.user.application.profile.dto.AvatarUploadRequest;
import com.wlf.user.application.profile.dto.UpdateProfileRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户接口：个人主页 / 改昵称 / 上传头像。
 * 全部接口要求登录
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * 改昵称 / 改学院。
     */
    @PutMapping("/me")
    public ApiResponse<UserInfo> updateProfile(@AuthenticationPrincipal AuthenticatedUser user,
                                               @Valid @RequestBody UpdateProfileRequest request) {
        return ApiResponse.ok(userService.updateProfile(user.userId(), request));
    }

    /**
     * 上传头像。
     */
    @PostMapping("/me/avatar")
    public ApiResponse<UserInfo> uploadAvatar(@AuthenticationPrincipal AuthenticatedUser user,
                                              @Valid @ModelAttribute AvatarUploadRequest request) {
        return ApiResponse.ok(userService.uploadAvatar(user.userId(), request.getFile()));
    }
}
