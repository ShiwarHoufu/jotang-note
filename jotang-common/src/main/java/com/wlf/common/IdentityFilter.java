package com.wlf.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Set;

/**
 * 无状态身份过滤器：
 * 只采信网关透传的身份头。
 *
 * <p>只填充 SecurityContext，不拒绝；40100 / 40300 仍由链尾统一产出。
 * 身份头只在 {@code X-Gateway-Token} 与本地配置一致时采信，防止绕过网关直连服务伪造。
 *
 * <p>刻意不加 {@code @Component}：
 * 加了会被 Spring Boot 当作普通 Servlet Filter自动注册进容器链，与 Security 链重复执行一次。
 * 由各 SecurityConfig 手工 new 并只注册进 Security 链。
 */
@Slf4j
public class IdentityFilter extends OncePerRequestFilter {

    /** 角色转为 Spring Security 权限时的前缀，与 hasRole(...) 的约定一致 */
    private static final String ROLE_PREFIX = "ROLE_";
    private static final Set<String> ROLES = Set.of("USER", "ADMIN");

    private final String gatewayToken;

    public IdentityFilter(String gatewayToken) {
        this.gatewayToken = gatewayToken;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            AuthenticatedUser user = fromGateway(request);
            if (user != null) {
                SecurityContextHolder.getContext().setAuthentication(toAuthentication(user));
            }
        }
        filterChain.doFilter(request, response);
    }

    /** 网关透传的身份头；网关令牌不匹配时一律不采信 */
    private AuthenticatedUser fromGateway(HttpServletRequest request) {
        if (gatewayToken == null || gatewayToken.isBlank()
                || !gatewayToken.equals(request.getHeader(IdentityHeaders.GATEWAY_TOKEN))) {
            return null;
        }
        String userId = request.getHeader(IdentityHeaders.USER_ID);
        String role = request.getHeader(IdentityHeaders.USER_ROLE);
        if (userId == null || role == null || !ROLES.contains(role)) {
            return null;
        }
        try {
            return new AuthenticatedUser(Long.valueOf(userId), "", role);
        } catch (NumberFormatException ex) {
            log.debug("网关身份头的 userId 不是数字：{}", userId);
            return null;
        }
    }

    private static UsernamePasswordAuthenticationToken toAuthentication(AuthenticatedUser user) {
        return new UsernamePasswordAuthenticationToken(
                user, null, List.of(new SimpleGrantedAuthority(ROLE_PREFIX + user.role())));
    }
}
