package com.wlf.user.application.result;

import com.wlf.storage.Bucket;
import com.wlf.storage.StorageService;
import com.wlf.user.domain.user.User;

/** 用户用例出参。 */
public record UserResult(Long id, String username, String nickname, Long collegeId,
                         String avatarUrl, String role) {

    public static UserResult from(User user, StorageService storageService) {
        return new UserResult(
                user.id(),
                user.username().value(),
                user.nickname().value(),
                user.collegeId().value(),
                user.avatar() == null
                        ? null
                        : storageService.publicUrl(Bucket.AVATAR, user.avatar().value()),
                user.role().name()
        );
    }
}
