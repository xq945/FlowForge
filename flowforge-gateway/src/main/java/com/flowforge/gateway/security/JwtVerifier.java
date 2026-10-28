package com.flowforge.gateway.security;

import com.flowforge.gateway.config.GatewaySecurityProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

/**
 * 网关侧 JWT 校验。
 *
 * <p>网关只做「是否放行」的第一道判断，**不解析出用户身份并让下游信任**。
 * 业务服务仍会独立校验签名，避免直连服务端口时被伪造请求头绕过。
 */
@Component
public class JwtVerifier {

    private static final int MIN_SECRET_BYTES = 32;

    private final SecretKey key;
    private final String issuer;

    public JwtVerifier(GatewaySecurityProperties properties) {
        String secret = properties.getJwt().getSecret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "flowforge.gateway.jwt.secret 未配置或长度不足 32 字节，请通过环境变量 FLOWFORGE_JWT_SECRET 注入");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.issuer = properties.getJwt().getIssuer();
    }

    public Jws<Claims> verify(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token);
    }
}
