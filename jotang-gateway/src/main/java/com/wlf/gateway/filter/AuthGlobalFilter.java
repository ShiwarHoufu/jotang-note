package com.wlf.gateway.filter;

import com.wlf.common.ApiResponse;
import com.wlf.common.AuthenticatedUser;
import com.wlf.common.ErrorCode;
import com.wlf.common.IdentityHeaders;
import com.wlf.common.JwtTokenProvider;
import com.wlf.common.PublicEndpoints;
import io.jsonwebtoken.JwtException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * 网关登录校验：无效令牌在入口直接拒绝，有效令牌把身份写进下游请求头。
 *
 * <p>本过滤器只负责“认证”；业务鉴权（是否本人 / 是否已下架）仍在各服务。
 */
@Slf4j
@Component
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final Set<String> PUBLIC_PATHS = Set.of(PublicEndpoints.PATHS);
    private static final String ADMIN_PATH_PREFIX = "/api/admin/";
    private static final String HEALTH_PATH_PREFIX = "/actuator/health";

    private final JwtTokenProvider jwtTokenProvider;
    private final ObjectMapper objectMapper;
    private final String gatewayToken;

    public AuthGlobalFilter(JwtTokenProvider jwtTokenProvider,
                            ObjectMapper objectMapper,
                            @Value("${jotang.gateway.token:dev-gateway-token}") String gatewayToken) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.objectMapper = objectMapper;
        this.gatewayToken = gatewayToken;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        ServerHttpRequest.Builder builder = request.mutate();
        // 下游只认网关头：JWT 不再透传，客户端自带的身份头也一律剥掉
        builder.headers(headers -> {
            headers.remove(HttpHeaders.AUTHORIZATION);
            headers.remove(IdentityHeaders.USER_ID);
            headers.remove(IdentityHeaders.USER_ROLE);
            headers.remove(IdentityHeaders.GATEWAY_TOKEN);
            headers.remove(IdentityHeaders.INTERNAL_TOKEN);
        });

        if (isPublic(path) || HttpMethod.OPTIONS.equals(request.getMethod())) {
            return chain.filter(withRequest(exchange, builder));
        }

        String token = extractToken(request);
        if (token == null) {
            return reject(exchange, ErrorCode.UNAUTHORIZED);
        }

        AuthenticatedUser user;
        try {
            user = jwtTokenProvider.parse(token);
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("网关 JWT 校验失败：{}", ex.getMessage());
            return reject(exchange, ErrorCode.UNAUTHORIZED);
        }

        if (path.startsWith(ADMIN_PATH_PREFIX) && !user.isAdmin()) {
            return reject(exchange, ErrorCode.FORBIDDEN);
        }

        builder.headers(headers -> {
            headers.set(IdentityHeaders.USER_ID, String.valueOf(user.userId()));
            headers.set(IdentityHeaders.USER_ROLE, user.role());
            headers.set(IdentityHeaders.GATEWAY_TOKEN, gatewayToken);
        });
        return chain.filter(withRequest(exchange, builder));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }

    private static ServerWebExchange withRequest(ServerWebExchange exchange, ServerHttpRequest.Builder builder) {
        return exchange.mutate().request(builder.build()).build();
    }

    private static boolean isPublic(String path) {
        return PUBLIC_PATHS.contains(path) || path.startsWith(HEALTH_PATH_PREFIX);
    }

    private static String extractToken(ServerHttpRequest request) {
        String header = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }

    private Mono<Void> reject(ServerWebExchange exchange, ErrorCode errorCode) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(errorCode.getHttpStatus());
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(errorBody(errorCode))));
    }

    private byte[] errorBody(ErrorCode errorCode) {
        try {
            return objectMapper.writeValueAsBytes(ApiResponse.fail(errorCode));
        } catch (Exception e) {
            // 错误响应自身不能再抛错：序列化失败时退回手写 JSON
            String json = "{\"code\":" + errorCode.getCode()
                    + ",\"message\":\"" + errorCode.getMessage() + "\",\"data\":null}";
            return json.getBytes(StandardCharsets.UTF_8);
        }
    }
}
