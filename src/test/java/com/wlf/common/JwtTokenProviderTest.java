package com.wlf.common;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.WeakKeyException;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link JwtTokenProvider} 的单元测试：签发 → 解析的往返，以及各类无效令牌的拒绝路径。
 *
 * <p>直接 {@code new} 而不走 Spring：构造函数是公开的，密钥与 TTL 都是普通入参，
 * 于是「密钥过短在构造时失败」「过期令牌」这类分支不必真的等时间或改配置。
 */
class JwtTokenProviderTest {

    private static final String SECRET = secretOf(32);
    private static final Duration TTL = Duration.ofHours(2);

    private final JwtTokenProvider provider = new JwtTokenProvider(SECRET, TTL);

    @Test
    void issueThenParseRoundTripsTheIdentity() {
        String token = provider.issue(42L, "zhangsan", "USER");

        AuthenticatedUser user = provider.parse(token);

        assertThat(user.userId()).isEqualTo(42L);
        assertThat(user.username()).isEqualTo("zhangsan");
        assertThat(user.role()).isEqualTo("USER");
    }

    /** Claims 按 §6.4：sub(userId) / username / role / jti / iat / exp */
    @Test
    void issuedTokenCarriesTheDocumentedClaims() {
        String token = provider.issue(7L, "lisi", "ADMIN");

        Claims claims = Jwts.parser().verifyWith(keyOf(SECRET)).build()
                .parseSignedClaims(token).getPayload();

        assertThat(claims.getSubject()).isEqualTo("7");
        assertThat(claims.get("username", String.class)).isEqualTo("lisi");
        assertThat(claims.get("role", String.class)).isEqualTo("ADMIN");
        // jti 是为 V2 的 Redis 黑名单预留的挂点，签发时就该带上
        assertThat(claims.getId()).isNotBlank();
        assertThat(claims.getExpiration()).isAfter(claims.getIssuedAt());
    }

    @Test
    void expiredTokenIsRejected() {
        JwtTokenProvider expired = new JwtTokenProvider(SECRET, Duration.ofSeconds(-1));
        String token = expired.issue(1L, "zhangsan", "USER");

        assertThatThrownBy(() -> expired.parse(token)).isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void tamperedTokenIsRejected() {
        String token = provider.issue(1L, "zhangsan", "USER");
        char last = token.charAt(token.length() - 1);
        String tampered = token.substring(0, token.length() - 1) + (last == 'A' ? 'B' : 'A');

        assertThatThrownBy(() -> provider.parse(tampered)).isInstanceOf(JwtException.class);
    }

    /** 别的密钥签出来的 token 验签应失败——这正是「密钥不落仓库、各环境独立」的意义 */
    @Test
    void tokenSignedWithAnotherKeyIsRejected() {
        String foreign = new JwtTokenProvider(secretOf(32), TTL).issue(1L, "zhangsan", "USER");

        assertThatThrownBy(() -> provider.parse(foreign)).isInstanceOf(JwtException.class);
    }

    @Test
    void malformedTokenIsRejected() {
        assertThatThrownBy(() -> provider.parse("not-a-jwt")).isInstanceOf(JwtException.class);
    }

    /** 密钥解码后不足 256 位：HS256 拒绝，且在构造时就失败，不留到签发 / 校验时才暴露 */
    @Test
    void secretShorterThan256BitsFailsAtConstruction() {
        assertThatThrownBy(() -> new JwtTokenProvider(secretOf(16), TTL))
                .isInstanceOf(WeakKeyException.class);
    }

    // ---- helpers ----------------------------------------------------------

    private static String secretOf(int bytes) {
        byte[] key = new byte[bytes];
        new SecureRandom().nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }

    private static SecretKey keyOf(String base64Secret) {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(base64Secret));
    }
}
