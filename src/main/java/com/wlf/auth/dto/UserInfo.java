package com.wlf.auth.dto;

import com.wlf.entity.User;

/**
 * 当前登录用户的对外视图，登录响应与 {@code GET /api/auth/me} 共用。
 * 见《概要设计》§5.1。
 *
 * <p>刻意不含 {@code passwordHash} 与 {@code email}：前者绝不能外传，
 * 后者在详情页 / 个人主页都用不到（§5.2 的主页也没有邮箱）。
 */
public record UserInfo(Long id, String username, String nickname, String avatarUrl, String role) {

    /**
     * avatarUrl 待用户资料模块接入 OSS 后，按 §6.7 拼公共读 Bucket 的公网地址；
     * 本阶段恒为 null，前端按「无头像」渲染。
     */
    public static UserInfo from(User user) {
        return new UserInfo(user.getId(), user.getUsername(), user.getNickname(), null, user.getRole());
    }
}
