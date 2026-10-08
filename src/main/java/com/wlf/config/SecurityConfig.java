package com.wlf.config;

import tools.jackson.databind.ObjectMapper;
import com.wlf.common.ApiResponse;
import com.wlf.common.ErrorCode;
import com.wlf.common.JwtAuthFilter;
import com.wlf.common.JwtTokenProvider;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.IOException;

/**
 * Spring Security 过滤器链：放行名单 + 无状态会话 + ADMIN 角色规则。
 * 见《概要设计》§5.6、§6.4。
 *
 * <p><b>40100 / 40300 的产出点在本类</b>，不在 GlobalExceptionHandler：
 * 认证与鉴权失败发生在过滤器链中，早于 DispatcherServlet，
 * {@code @RestControllerAdvice} 收不到。这里用 AuthenticationEntryPoint
 * 与 AccessDeniedHandler 手工写出统一响应体。
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * 免登录接口（§5.1、§5.3）。
     *
     * <p>{@code /api/colleges} 也必须放行，理由与密码找回同源：注册页要拿它填学院下拉框，
     * 而注册发生在登录之前。不放行则下拉框取不到数据，谁也注册不了。
     *
     * <p>与之相对，{@code /api/courses} 与 {@code /api/tags} 刻意**不放行**——浏览内容需登录。
     */
    private static final String[] PUBLIC_ENDPOINTS = {
            "/api/auth/register",
            "/api/auth/login",
            "/api/auth/password/forgot",
            "/api/auth/password/reset",
            "/api/colleges"
    };

    private final ObjectMapper objectMapper;
    private final JwtTokenProvider jwtTokenProvider;

    public SecurityConfig(ObjectMapper objectMapper, JwtTokenProvider jwtTokenProvider) {
        this.objectMapper = objectMapper;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 无状态 JWT，不依赖 Cookie 会话，CSRF 不适用
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        // 管理端（§5.6）。JwtAuthFilter 已把 JWT 里的 role 包成 ROLE_ 前缀的权限，
                        // hasRole("ADMIN") 正好对上，不需要在这里再解一次 token。
                        // 两条失败路径都是现成的：未登录 → AuthenticationEntryPoint 出 40100，
                        // 已登录但非管理员 → AccessDeniedHandler 出 40300（§5.7）
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        // 其余接口一律要求登录（§5：全部内容需登录）
                        .anyRequest().authenticated())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(unauthorizedEntryPoint())
                        .accessDeniedHandler(forbiddenHandler()))
                .addFilterBefore(new JwtAuthFilter(jwtTokenProvider), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** 未登录 / token 无效或过期 → 40100（§5.7，三种情形合一） */
    private AuthenticationEntryPoint unauthorizedEntryPoint() {
        return (request, response, authException) -> writeError(response, ErrorCode.UNAUTHORIZED);
    }

    /** 已登录但无权限（非本人 / 非管理员）→ 40300 */
    private AccessDeniedHandler forbiddenHandler() {
        return (request, response, accessDeniedException) -> writeError(response, ErrorCode.FORBIDDEN);
    }

    /**
     * 手工写出统一响应体。此处不在 MVC 流程内，拿不到 {@code @RestControllerAdvice}，
     * 只能自己序列化；复用 ApiResponse 保证形状与其他接口完全一致。
     */
    private void writeError(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        response.setStatus(errorCode.getHttpStatus().value());
        response.setContentType("application/json;charset=UTF-8");
        objectMapper.writeValue(response.getWriter(), ApiResponse.fail(errorCode));
    }
}
