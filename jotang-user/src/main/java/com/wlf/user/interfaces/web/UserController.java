package com.wlf.user.interfaces.web;

import com.wlf.user.application.command.UpdateProfileCommand;
import com.wlf.user.application.command.UploadAvatarCommand;
import com.wlf.user.application.service.UserService;
import com.wlf.user.application.result.UserResult;
import com.wlf.common.ApiResponse;
import com.wlf.common.AuthenticatedUser;
import com.wlf.user.interfaces.web.assembler.UserAssembler;
import com.wlf.user.interfaces.web.dto.AvatarUploadRequest;
import com.wlf.user.interfaces.web.dto.UpdateProfileRequest;
import com.wlf.user.interfaces.web.dto.UserResponse;
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
    private final UserAssembler userAssembler;

    public UserController(UserService userService, UserAssembler userAssembler) {
        this.userService = userService;
        this.userAssembler = userAssembler;
    }

    /**
     * 改昵称 / 改学院。
     */
    @PutMapping("/me")
    public ApiResponse<UserResponse> updateProfile(@AuthenticationPrincipal AuthenticatedUser user,
                                                   @Valid @RequestBody UpdateProfileRequest request) {
        UpdateProfileCommand command = userAssembler.toCommand(user.userId(), request);
        UserResult result = userService.updateProfile(command);
        return ApiResponse.ok(userAssembler.toResponse(result));
    }

    /**
     * 上传头像。
     */
    @PostMapping("/me/avatar")
    public ApiResponse<UserResponse> uploadAvatar(@AuthenticationPrincipal AuthenticatedUser user,
                                                  @Valid @ModelAttribute AvatarUploadRequest request) {
        UploadAvatarCommand command = userAssembler.toCommand(user.userId(), request);
        UserResult result = userService.uploadAvatar(command);
        return ApiResponse.ok(userAssembler.toResponse(result));
    }
}
