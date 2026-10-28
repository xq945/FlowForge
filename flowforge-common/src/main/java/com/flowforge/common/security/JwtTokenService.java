package com.flowforge.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;

/**
 * JWT 签发与解析。
 *
 * <p>所有服务使用同一套密钥与 issuer：网关负责快速拒绝无效请求，
 * 业务服务负责在各自进程内真正建立身份，不信任任何上游传来的身份头。
 */
public class JwtTokenService {

    private static final int MIN_SECRET_BYTES = 32;

    private final SecretKey key;
    private final JwtProperties properties;

    public JwtTokenService(JwtProperties properties) {
        String secret = properties.getSecret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "flowforge.security.jwt.secret 未配置或长度不足 32 字节，请通过环境变量 FLOWFORGE_JWT_SECRET 注入");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.properties = properties;
    }

    public String issue(LoginUser user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(user.userId()))
                .issuer(properties.getIssuer())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(properties.getTtl())))
                .claim("username", user.username())
                .claim("roles", user.roles())
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * 解析并校验签名、过期时间与 issuer。校验失败抛 {@link io.jsonwebtoken.JwtException}。
     */
    public LoginUser parse(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .requireIssuer(properties.getIssuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        List<?> rawRoles = claims.get("roles", List.class);
        List<String> roles = rawRoles == null ? List.of()
                : rawRoles.stream().map(String::valueOf).toList();

        return new LoginUser(
                Long.valueOf(claims.getSubject()),
                claims.get("username", String.class),
                roles);
    }

    public long ttlSeconds() {
        return properties.getTtl().toSeconds();
    }
}
