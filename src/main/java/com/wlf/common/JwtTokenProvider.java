package com.wlf.common;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * JWT 签发与校验（HS256，密钥走环境变量，TTL 2 小时）。
 * 见《概要设计》§6.4。
 *
 * <p>密钥按 <b>Base64 解码</b>后使用（配置里是 32 字节密钥的 Base64 串），
 * 解码后须 ≥256 位，否则 HS256 拒绝——缺密钥或过短都在启动时直接失败，
 * 不留到运行时才暴露。
 *
 * <p>TTL 短是为了缓解「无法即时失效」（§8.4 已知限制 1），
 * 退出 / 改密后旧 token 在到期前仍然有效，服务端不维护失效名单。
 */
@Component
public class JwtTokenProvider {

    private final SecretKey key;
    private final Duration ttl;

    public JwtTokenProvider(@Value("${jwt.secret}") String base64Secret,
                            @Value("${jwt.ttl:2h}") Duration ttl) {
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(base64Secret));
        this.ttl = ttl;
    }

    /**
     * 签发令牌。Claims 按 §6.4：sub(userId) / username / role / iat / exp / jti。
     *
     * <p>jti 现在没用上，是为 V2 的「Redis 黑名单」预留的挂点（§6.4）。
     */
    public String issue(Long userId, String username, String role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .claim("role", role)
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key)
                .compact();
    }

    /**
     * 校验并解析令牌。
     *
     * <p>签名不符、格式错误、已过期一律抛 {@code JwtException}，
     * 由调用方（JwtAuthFilter）吞掉并当作「未认证」处理——
     * 不区分具体原因，对应 §5.7 中 40100 的三种情形合一。
     */
    public AuthenticatedUser parse(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return new AuthenticatedUser(
                Long.valueOf(claims.getSubject()),
                claims.get("username", String.class),
                claims.get("role", String.class));
    }
}
