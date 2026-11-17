package com.wlf.note.config;

import com.wlf.common.ApiResponse;
import com.wlf.common.ErrorCode;
import com.wlf.common.IdentityFilter;
import com.wlf.common.JwtTokenProvider;
import com.wlf.common.PublicEndpoints;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
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
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

/**
 * Spring Security 过滤器链：放行名单 + 无状态会话 + ADMIN 角色规则。
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * 免登录接口。与网关共用一份清单，避免两处漂移。
     *
     * <p>清单里 user 侧的几条在 note-app 里没有对应路由，留着无副作用；
     * 共用清单换来的是「新增放行点只改一处」。
     */
    private static final String[] PUBLIC_ENDPOINTS = PublicEndpoints.PATHS;

    private final ObjectMapper objectMapper;
    private final JwtTokenProvider jwtTokenProvider;
    private final String gatewayToken;

    public SecurityConfig(ObjectMapper objectMapper,
                          JwtTokenProvider jwtTokenProvider,
                          @Value("${jotang.gateway.token:dev-gateway-token}") String gatewayToken) {
        this.objectMapper = objectMapper;
        this.jwtTokenProvider = jwtTokenProvider;
        this.gatewayToken = gatewayToken;
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
                        // 服务间内部接口：不走用户 JWT，由 InternalTokenInterceptor 校验服务令牌
                        .requestMatchers("/internal/**").permitAll()
                        // 管理端（§5.6）。IdentityFilter 已把 role 包成 ROLE_ 前缀的权限，
                        // hasRole("ADMIN") 正好对上，不需要在这里再解一次 token。
                        // 两条失败路径都是现成的：未登录 → AuthenticationEntryPoint 出 40100，
                        // 已登录但非管理员 → AccessDeniedHandler 出 40300（§5.7）
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        // 其余接口一律要求登录
                        .anyRequest().authenticated())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(unauthorizedEntryPoint())
                        .accessDeniedHandler(forbiddenHandler()))
                .addFilterBefore(new IdentityFilter(jwtTokenProvider, gatewayToken),
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * 过渡期测试夹具需要：test scope 的 jotang-user 里 AuthService 等要它。
     * note 自身不使用；运行时没有 user 模块，这个 Bean 就闲在这。
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** 未登录 / token 无效或过期 → 40100 */
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
