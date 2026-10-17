package com.wlf.auth.dto;

import com.wlf.entity.User;
import com.wlf.storage.Bucket;
import com.wlf.storage.StorageService;

/**
 * 当前登录用户的对外视图，登录响应与 {@code GET /api/auth/me} 共用。
 */
public record UserInfo(Long id, String username, String nickname, Long collegeId,
                       String avatarUrl, String role) {

    /**
     * 装配当前登录用户的对外视图。
     * {@code avatarUrl} 由 {@code user.avatar} 那个对象键现拼成公共读 Bucket 的公网地址。
     *
     * <p><b>刻意不保留一个不带 StorageService 的重载</b>：那样会留下一条静默产出
     * {@code avatarUrl == null} 的路径，而本方法有三个调用点（注册 / 登录 / 取当前用户），
     * 漏改一处就是「有头像的用户在某个接口上忽然没头像」这种极难发现的 bug。
     */
    public static UserInfo from(User user, StorageService storageService) {
        return new UserInfo(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getCollegeId(),
                avatarUrl(user, storageService),
                user.getRole()
        );
    }

    /**
     * 头像对象键 → 完整公网 URL。
     */
    private static String avatarUrl(User user, StorageService storageService) {
        return user.getAvatar() == null ? null : storageService.publicUrl(Bucket.AVATAR, user.getAvatar());
    }
}
