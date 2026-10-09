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
 * 见《概要设计》§5.2。
 *
 * <p>当前已落地改资料与头像上传；个人主页随后续切片补上。
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
     *
     * <p><b>改的是谁取自 JWT，绝不接受请求体传入</b>——否则任何人都能改别人的资料。
     * 请求体里就算带了 {@code id} / {@code userId}，也只会被 Jackson 静默丢弃
     * （未知字段不报错），改的仍是 token 的主人。
     *
     * <p><b>出参是本模块唯一一次引用 {@code auth} 模块的 DTO。</b>方向是刻意的：
     * {@code UserInfo} 就是「当前登录用户的对外视图」，登录与 {@code GET /api/auth/me} 已经
     * 在发这个形状，改完资料再发一份别的形状只会让前端多一套映射。跨模块复用 DTO 在
     * {@code favorite/dto/FavoriteItemResponse}（引用 note 的 {@code UploaderResponse} 与
     * catalog 的 {@code CourseResponse} / {@code TagResponse}）已有先例，§5.4 也明说
     * 「同一个东西在契约里声明两遍就成了同一规则的两份副本」。
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
     * 上传头像。multipart 表单，单个 {@code file} 字段。见 §6.7。
     *
     * <p>换谁的取自 JWT，与改资料同一规矩。出参同样是 {@code UserInfo}——头像的 OSS 键由服务端
     * 生成，前端推不出来，必须把新 URL 拿回去；而它本就在 Pinia 里存着整个 {@code UserInfo}，
     * 回同一形状让前端「一把覆盖」，不必为这一个字段单独写一条赋值分支。
     *
     * <p>注意这里的「回资源」与改资料的理由不同：改资料是「前端要覆盖昵称与学院」，
     * 这里是「前端拿不到那个 URL」。但落到契约上是同一件事，故形状保持一致。
     */
    @PostMapping("/me/avatar")
    public ApiResponse<UserInfo> uploadAvatar(@AuthenticationPrincipal AuthenticatedUser user,
                                              @Valid @ModelAttribute AvatarUploadRequest request) {
        return ApiResponse.ok(userService.uploadAvatar(user.userId(), request.getFile()));
    }
}
