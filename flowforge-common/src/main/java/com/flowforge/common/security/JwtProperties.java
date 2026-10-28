package com.flowforge.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * JWT 配置。所有服务共享同一把密钥，只能通过环境变量注入。
 */
@ConfigurationProperties(prefix = "flowforge.security.jwt")
public class JwtProperties {

    private String secret;

    private String issuer = "flowforge";

    /** 访问令牌有效期；第一版不做 refresh token，过期重新登录即可 */
    private Duration ttl = Duration.ofHours(2);

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public Duration getTtl() {
        return ttl;
    }

    public void setTtl(Duration ttl) {
        this.ttl = ttl;
    }
}
