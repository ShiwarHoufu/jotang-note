package com.wlf.common;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link IdentityFilter} 的两条身份来源：网关透传的身份头优先，直连 Bearer JWT 兜底。
 *
 * <p>不可信的头 / 坏 token 一律当作匿名，由链尾统一产出 40100。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class IdentityFilterTest {

    /** 受保护接口（不在放行名单里）。坏身份不会走到查库 */
    private static final String PROTECTED = "/api/notes/mine";

    @Autowired
    private MockMvc mockMvc;

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jotang.gateway.token:dev-gateway-token}")
    private String gatewayToken;

    // ---- 网关身份头 ---------------------------------------------------------

    @Test
    void gatewayHeadersAreAcceptedWhenTokenMatches() throws Exception {
        mockMvc.perform(get("/api/notes/mine")
                        .header(IdentityHeaders.GATEWAY_TOKEN, gatewayToken)
                        .header(IdentityHeaders.USER_ID, "999999")
                        .header(IdentityHeaders.USER_ROLE, "USER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void gatewayHeadersAreIgnoredWhenTokenDoesNotMatch() throws Exception {
        mockMvc.perform(get(PROTECTED)
                        .header(IdentityHeaders.GATEWAY_TOKEN, "wrong-token")
                        .header(IdentityHeaders.USER_ID, "1")
                        .header(IdentityHeaders.USER_ROLE, "ADMIN"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));
    }

    @Test
    void gatewayHeadersAreIgnoredWithoutToken() throws Exception {
        mockMvc.perform(get(PROTECTED)
                        .header(IdentityHeaders.USER_ID, "1")
                        .header(IdentityHeaders.USER_ROLE, "ADMIN"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));
    }

    @Test
    void malformedGatewayUserIdIsIgnored() throws Exception {
        mockMvc.perform(get(PROTECTED)
                        .header(IdentityHeaders.GATEWAY_TOKEN, gatewayToken)
                        .header(IdentityHeaders.USER_ID, "not-a-number")
                        .header(IdentityHeaders.USER_ROLE, "USER"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));
    }

    // ---- Bearer JWT 兜底 ----------------------------------------------------

    @Test
    void anonymousRequestIsRejectedWith40100() throws Exception {
        mockMvc.perform(get(PROTECTED))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));
    }

    @Test
    void malformedBearerTokenIsRejectedExactlyLikeAnonymous() throws Exception {
        assertSameAsAnonymous(perform("Bearer not-a-jwt"));
    }

    /**
     * 篡改点必须落在 token <b>中间</b>，不能图省事动最后一位：
     * JWT 各段是 base64url，段末字符的低位是填充、解码时被丢弃——
     * 改末位可能解出完全相同的字节，签名照样验过（实测会一路走到 controller 报 40400）。
     */
    @Test
    void tamperedTokenIsRejectedExactlyLikeAnonymous() throws Exception {
        String valid = provider(Duration.ofHours(2)).issue(1L, "zhangsan", "USER");
        int at = valid.length() / 2;
        char replacement = valid.charAt(at) == 'a' ? 'b' : 'a';
        String tampered = valid.substring(0, at) + replacement + valid.substring(at + 1);

        assertSameAsAnonymous(perform("Bearer " + tampered));
    }

    @Test
    void expiredTokenIsRejectedExactlyLikeAnonymous() throws Exception {
        String expired = provider(Duration.ofSeconds(-1)).issue(1L, "zhangsan", "USER");

        assertSameAsAnonymous(perform("Bearer " + expired));
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejectedExactlyLikeAnonymous() throws Exception {
        String foreign = new JwtTokenProvider(
                java.util.Base64.getEncoder().encodeToString(new byte[32]), Duration.ofHours(2))
                .issue(1L, "zhangsan", "USER");

        assertSameAsAnonymous(perform("Bearer " + foreign));
    }

    // ---- helpers ----------------------------------------------------------

    private JwtTokenProvider provider(Duration ttl) {
        return new JwtTokenProvider(secret, ttl);
    }

    /** 带指定 Authorization 头打受保护接口，断言 401 / 40100 */
    private MvcResult perform(String authorization) throws Exception {
        MockHttpServletRequestBuilder request = get(PROTECTED);
        if (authorization != null) {
            request = request.header("Authorization", authorization);
        }
        return mockMvc.perform(request)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100))
                .andReturn();
    }

    private void assertSameAsAnonymous(MvcResult badToken) throws Exception {
        String anonymous = perform(null).getResponse().getContentAsString(StandardCharsets.UTF_8);
        String rejected = badToken.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(rejected).isEqualTo(anonymous);
    }
}
