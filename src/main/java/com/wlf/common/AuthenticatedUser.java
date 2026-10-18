package com.wlf.common;

/**
 * 认证主体：JWT 校验通过后由 JwtAuthFilter 放入 SecurityContext。
 *
 * <p>Controller 用 {@code @AuthenticationPrincipal} 直接取到，
 * 免去到处写 {@code SecurityContextHolder.getContext().getAuthentication()} 的长链。
 *
 * @param userId   用户 id，取自 JWT 的 sub
 * @param username 登录名，仅用于展示与日志
 * @param role     USER / ADMIN，管理员接口据此判定
 */
public record AuthenticatedUser(Long userId, String username, String role) {

    private static final String ADMIN_ROLE = "ADMIN";

    /**
     * 是否管理员。
     */
    public boolean isAdmin() {
        return ADMIN_ROLE.equals(role);
    }
}
