package com.wlf.common;

/**
 * 认证主体：JWT 校验通过后由 JwtAuthFilter 放入 SecurityContext。
 *
 * <p>Controller 用 {@code @AuthenticationPrincipal} 直接取到，
 * 免去到处写 {@code SecurityContextHolder.getContext().getAuthentication()} 的长链。
 *
 * <p>只装鉴权必需的三项，不放整个 {@code User} 实体——避免了每次请求都查一次库，
 * 也让密码哈希这类敏感字段天然不可能被带进业务层。
 *
 * @param userId   用户 id，取自 JWT 的 sub
 * @param username 登录名，仅用于展示与日志
 * @param role     USER / ADMIN，管理员接口据此判定
 */
public record AuthenticatedUser(Long userId, String username, String role) {

    private static final String ADMIN_ROLE = "ADMIN";

    /**
     * 是否管理员。
     *
     * <p>管理端整条路径的权限由 {@code SecurityConfig} 的 {@code /api/admin/**} 规则判；
     * 这里给「同一个接口内按角色分流」的场合用，见 {@code NoteService#requireReadable}。
     */
    public boolean isAdmin() {
        return ADMIN_ROLE.equals(role);
    }
}
