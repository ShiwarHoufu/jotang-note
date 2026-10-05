package com.wlf.common;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * 无状态 JWT 过滤器：解析 {@code Authorization: Bearer}，填充 SecurityContext。
 * 见《概要设计》§1.1、§6.4。
 *
 * <p><b>只填充，不拒绝。</b>token 缺失、无效或过期时什么都不设，请求以匿名身份继续往下走，
 * 由链尾的 AuthorizationFilter 因 {@code anyRequest().authenticated()} 未满足而拒绝，
 * 再经 ExceptionTranslationFilter 触发 AuthenticationEntryPoint 产出 40100。
 *
 * <p>之所以不能在这里直接抛异常或写响应：本过滤器被插在
 * {@code UsernamePasswordAuthenticationFilter} 之前，位置在 ExceptionTranslationFilter
 * <b>上游</b>，抛出的异常不会被翻译，写出的响应也会绕开统一响应体。
 *
 * <p>本类<b>刻意不加 {@code @Component}</b>：加了会被 Spring Boot 当作普通 Servlet Filter
 * 自动注册进容器链，与 Security 链重复执行一次（且容器链那次在 SecurityContextHolderFilter
 * 之前跑，设进去的认证态随后会被覆盖）。由 SecurityConfig 手工 new 并只注册进 Security 链。
 */
@Slf4j
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    /** 角色转为 Spring Security 权限时的前缀，与 hasRole(...) 的约定一致 */
    private static final String ROLE_PREFIX = "ROLE_";

    private final JwtTokenProvider jwtTokenProvider;

    public JwtAuthFilter(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = extractToken(request);

        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                AuthenticatedUser user = jwtTokenProvider.parse(token);
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        user, null, List.of(new SimpleGrantedAuthority(ROLE_PREFIX + user.role())));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (JwtException | IllegalArgumentException ex) {
                // 仅 debug：过期 token 是常态，不该刷 warn。按未认证处理，交由链尾拒绝。
                log.debug("JWT 校验失败，按未认证处理：{}", ex.getMessage());
            }
        }

        filterChain.doFilter(request, response);
    }

    private static String extractToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }
}
