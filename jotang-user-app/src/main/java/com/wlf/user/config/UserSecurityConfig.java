package com.wlf.user.config;

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
 * 用户服务的过滤器链：放行名单 + 无状态会话。
 */
@Configuration
@EnableWebSecurity
public class UserSecurityConfig {

    private static final String[] PUBLIC_ENDPOINTS = PublicEndpoints.PATHS;

    private final ObjectMapper objectMapper;
    private final JwtTokenProvider jwtTokenProvider;
    private final String gatewayToken;

    public UserSecurityConfig(ObjectMapper objectMapper,
                              JwtTokenProvider jwtTokenProvider,
                              @Value("${jotang.gateway.token:dev-gateway-token}") String gatewayToken) {
        this.objectMapper = objectMapper;
        this.jwtTokenProvider = jwtTokenProvider;
        this.gatewayToken = gatewayToken;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        // 服务间内部接口：不走用户身份，由 InternalTokenInterceptor 校验服务令牌
                        .requestMatchers("/internal/**").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(unauthorizedEntryPoint())
                        .accessDeniedHandler(forbiddenHandler()))
                .addFilterBefore(new IdentityFilter(jwtTokenProvider, gatewayToken),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    private AuthenticationEntryPoint unauthorizedEntryPoint() {
        return (request, response, authException) -> writeError(response, ErrorCode.UNAUTHORIZED);
    }

    private AccessDeniedHandler forbiddenHandler() {
        return (request, response, accessDeniedException) -> writeError(response, ErrorCode.FORBIDDEN);
    }

    private void writeError(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        response.setStatus(errorCode.getHttpStatus().value());
        response.setContentType("application/json;charset=UTF-8");
        objectMapper.writeValue(response.getWriter(), ApiResponse.fail(errorCode));
    }
}
