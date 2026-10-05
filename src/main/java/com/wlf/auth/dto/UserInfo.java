package com.wlf.auth.dto;

import com.wlf.entity.User;

/**
 * 当前登录用户的对外视图，登录响应与 {@code GET /api/auth/me} 共用。
 * 见《概要设计》§5.1。
 *
 * <p>刻意不含 {@code passwordHash} 与 {@code email}：前者绝不能外传，
 * 后者在详情页 / 个人主页都用不到（§5.2 的主页也没有邮箱）。
 *
 * <p>{@code collegeId} 只给 id 不给学院名（D8）：前端本来就要拉
 * {@code GET /api/colleges} 填注册下拉框，手里有 id→name 的映射，
 * 免得后端为每次登录/取当前用户多查一次 college 表。
 */
public record UserInfo(Long id, String username, String nickname, Long collegeId,
                       String avatarUrl, String role) {

    /**
     * avatarUrl 待用户资料模块接入 OSS 后，按 §6.7 拼公共读 Bucket 的公网地址；
     * 本阶段恒为 null，前端按「无头像」渲染。
     */
    public static UserInfo from(User user) {
        return new UserInfo(user.getId(), user.getUsername(), user.getNickname(),
                user.getCollegeId(), null, user.getRole());
    }
}
