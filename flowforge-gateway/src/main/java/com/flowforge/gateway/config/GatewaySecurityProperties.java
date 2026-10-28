package com.flowforge.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * 网关鉴权配置。
 *
 * <p>密钥与用户服务共享，**只能通过环境变量注入**：配置文件里出现真实密钥就等于泄露。
 */
@ConfigurationProperties(prefix = "flowforge.gateway")
public class GatewaySecurityProperties {

    /** 无需认证即可访问的路径（Ant 风格） */
    private List<String> whitelist = List.of();

    private final Jwt jwt = new Jwt();

    public List<String> getWhitelist() {
        return whitelist;
    }

    public void setWhitelist(List<String> whitelist) {
        this.whitelist = whitelist;
    }

    public Jwt getJwt() {
        return jwt;
    }

    public static class Jwt {

        /** HS256 密钥，长度需 ≥ 32 字节 */
        private String secret;

        /** 期望的签发者 */
        private String issuer = "flowforge";

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
    }
}
