package com.wlf.user;

import com.wlf.auth.dto.UserInfo;
import com.wlf.common.ApiResponse;
import com.wlf.common.AuthenticatedUser;
import com.wlf.user.dto.AvatarUploadRequest;
import com.wlf.user.dto.UpdateProfileRequest;
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
 *
 * <p>当前已落地改资料与头像上传；个人主页随后续切片补上。
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
     *
     * <p>出参是本模块唯一一次引用 {@code auth} 模块的 DTO。方向是刻意的：
     * {@code UserInfo} 就是「当前登录用户的对外视图」，登录与 {@code GET /api/auth/me} 已经
     * 在发这个形状，改完资料再发一份别的形状只会让前端多一套映射。
     *
     * <p>回显资源而不是像 {@code PUT /api/notes/{id}} 那样回 {@code data:null}，是因为
     * 前端拿它直接在 Pinia 里覆盖昵称与学院；而笔记编辑之后详情页本就要重开一次，那份响应没什么可带回来的。
     */
    @PutMapping("/me")
    public ApiResponse<UserInfo> updateProfile(@AuthenticationPrincipal AuthenticatedUser user,
                                               @Valid @RequestBody UpdateProfileRequest request) {
        return ApiResponse.ok(userService.updateProfile(user.userId(), request));
    }

    /**
     * 上传头像。multipart 表单，单个 {@code file} 字段。
     * 出参同样是 {@code UserInfo}——头像的 OSS 键由服务端生成，前端推不出来，必须把新 URL 拿回去；
     * 而它本就在 Pinia 里存着整个 {@code UserInfo}，所以回同一形状。
     */
    @PostMapping("/me/avatar")
    public ApiResponse<UserInfo> uploadAvatar(@AuthenticationPrincipal AuthenticatedUser user,
                                              @Valid @ModelAttribute AvatarUploadRequest request) {
        return ApiResponse.ok(userService.uploadAvatar(user.userId(), request.getFile()));
    }
}
