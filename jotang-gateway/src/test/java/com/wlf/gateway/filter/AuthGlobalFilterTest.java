package com.wlf.gateway.filter;

import com.wlf.common.IdentityHeaders;
import com.wlf.common.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class AuthGlobalFilterTest {

    private static final String SECRET = "YWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWE=";
    private static final String GATEWAY_TOKEN = "test-gateway-token";

    private JwtTokenProvider provider;
    private AuthGlobalFilter filter;

    @BeforeEach
    void setUp() {
        provider = new JwtTokenProvider(SECRET, Duration.ofHours(1));
        filter = new AuthGlobalFilter(provider, new ObjectMapper(), GATEWAY_TOKEN);
    }

    @Test
    void publicPathPassesWithoutTokenAndStripsSpoofedHeaders() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/auth/login")
                        .header(IdentityHeaders.USER_ID, "999")
                        .header(IdentityHeaders.GATEWAY_TOKEN, "forged")
                        .build());
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

        filter.filter(exchange, chain(forwarded)).block();

        assertThat(forwarded.get()).isNotNull();
        var headers = forwarded.get().getRequest().getHeaders();
        assertThat(headers.getFirst(IdentityHeaders.USER_ID)).isNull();
        assertThat(headers.getFirst(IdentityHeaders.GATEWAY_TOKEN)).isNull();
    }

    @Test
    void protectedPathWithoutTokenReturns40100() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/notes").build());
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

        filter.filter(exchange, chain(forwarded)).block();

        assertThat(forwarded.get()).isNull();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(body(exchange)).contains("\"code\":40100");
    }

    @Test
    void malformedTokenReturns40100() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/notes")
                        .header("Authorization", "Bearer not-a-token")
                        .build());
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

        filter.filter(exchange, chain(forwarded)).block();

        assertThat(forwarded.get()).isNull();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(body(exchange)).contains("\"code\":40100");
    }

    @Test
    void expiredTokenReturns40100() {
        JwtTokenProvider expiredProvider = new JwtTokenProvider(SECRET, Duration.ofSeconds(-1));
        String token = expiredProvider.issue(42L, "zhangsan", "USER");
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/notes")
                        .header("Authorization", "Bearer " + token)
                        .build());

        filter.filter(exchange, chain(new AtomicReference<>())).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(body(exchange)).contains("\"code\":40100");
    }

    @Test
    void validTokenInjectsIdentityAndStripsSpoofedHeaders() {
        String token = provider.issue(42L, "zhangsan", "USER");
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/notes")
                        .header("Authorization", "Bearer " + token)
                        .header(IdentityHeaders.USER_ID, "999")
                        .header(IdentityHeaders.USER_ROLE, "ADMIN")
                        .header(IdentityHeaders.GATEWAY_TOKEN, "forged")
                        .header(IdentityHeaders.INTERNAL_TOKEN, "forged")
                        .build());
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

        filter.filter(exchange, chain(forwarded)).block();

        var headers = forwarded.get().getRequest().getHeaders();
        assertThat(headers.getFirst(HttpHeaders.AUTHORIZATION)).isNull();
        assertThat(headers.getFirst(IdentityHeaders.USER_ID)).isEqualTo("42");
        assertThat(headers.getFirst(IdentityHeaders.USER_ROLE)).isEqualTo("USER");
        assertThat(headers.getFirst(IdentityHeaders.GATEWAY_TOKEN)).isEqualTo(GATEWAY_TOKEN);
        assertThat(headers.getFirst(IdentityHeaders.INTERNAL_TOKEN)).isNull();
    }

    @Test
    void adminPathRejectsNonAdmin() {
        String token = provider.issue(42L, "zhangsan", "USER");
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/admin/notes/1/offline")
                        .header("Authorization", "Bearer " + token)
                        .build());
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

        filter.filter(exchange, chain(forwarded)).block();

        assertThat(forwarded.get()).isNull();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(body(exchange)).contains("\"code\":40300");
    }

    @Test
    void adminPathAllowsAdmin() {
        String token = provider.issue(7L, "admin", "ADMIN");
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/admin/notes/1/offline")
                        .header("Authorization", "Bearer " + token)
                        .build());
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

        filter.filter(exchange, chain(forwarded)).block();

        assertThat(forwarded.get()).isNotNull();
        assertThat(forwarded.get().getRequest().getHeaders().getFirst(IdentityHeaders.USER_ROLE))
                .isEqualTo("ADMIN");
    }

    @Test
    void optionsPreflightPassesWithoutToken() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.method(HttpMethod.OPTIONS, URI.create("/api/notes")).build());
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

        filter.filter(exchange, chain(forwarded)).block();

        assertThat(forwarded.get()).isNotNull();
    }

    private static GatewayFilterChain chain(AtomicReference<ServerWebExchange> forwarded) {
        return exchange -> {
            forwarded.set(exchange);
            return Mono.empty();
        };
    }

    private static String body(MockServerWebExchange exchange) {
        DataBuffer buffer = DataBufferUtils.join(exchange.getResponse().getBody()).block();
        if (buffer == null) {
            return "";
        }
        byte[] bytes = new byte[buffer.readableByteCount()];
        buffer.read(bytes);
        DataBufferUtils.release(buffer);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
