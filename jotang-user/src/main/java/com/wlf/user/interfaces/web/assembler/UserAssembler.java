package com.wlf.user.interfaces.web.assembler;

import com.wlf.user.application.command.UpdateProfileCommand;
import com.wlf.user.application.command.UploadAvatarCommand;
import com.wlf.user.application.result.UserResult;
import com.wlf.user.interfaces.web.dto.AvatarUploadRequest;
import com.wlf.user.interfaces.web.dto.UpdateProfileRequest;
import com.wlf.user.interfaces.web.dto.UserResponse;
import org.springframework.stereotype.Component;

/** 用户接口的 Request/Response 与 Command/Result 映射。 */
@Component
public class UserAssembler {

    public UpdateProfileCommand toCommand(Long userId, UpdateProfileRequest request) {
        return new UpdateProfileCommand(userId, request.getNickname(), request.getCollegeId());
    }

    public UploadAvatarCommand toCommand(Long userId, AvatarUploadRequest request) {
        return new UploadAvatarCommand(userId, new MultipartUploadedFile(request.getFile()));
    }

    public UserResponse toResponse(UserResult result) {
        return new UserResponse(result.id(), result.username(), result.nickname(),
                result.collegeId(), result.avatarUrl(), result.role());
    }
}
