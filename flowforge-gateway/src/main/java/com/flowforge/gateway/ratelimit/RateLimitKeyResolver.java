package com.flowforge.gateway.ratelimit;

import com.flowforge.gateway.filter.GatewayAttributes;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;

import java.net.InetSocketAddress;

/**
 * 限流键解析。
 *
 * <p>已认证请求按用户计数，匿名请求只能按客户端 IP 兜底。
 * 用户 ID 只认 {@link GatewayAttributes#USER_ID}：它由 JWT 校验通过后写入，
 * 白名单路径上不存在，正好让登录、注册这类匿名接口落到 IP 维度。
 */
@Component
public class RateLimitKeyResolver {

    private static final String USER_PREFIX = "rl:user:";
    private static final String IP_PREFIX = "rl:ip:";
    private static final String UNKNOWN_IP = "unknown";

    public String resolve(ServerWebExchange exchange) {
        Object userId = exchange.getAttributes().get(GatewayAttributes.USER_ID);
        if (userId != null && StringUtils.hasText(String.valueOf(userId))) {
            return USER_PREFIX + userId;
        }
        return IP_PREFIX + clientIp(exchange.getRequest());
    }

    /**
     * 客户端 IP。
     *
     * <p>优先级 {@code X-Real-IP} → {@code X-Forwarded-For} 首段 → 连接地址：
     * 前端 nginx 只设置了 {@code X-Real-IP}，容器部署时网关也不映射宿主机端口，
     * 外部无法绕过 nginx 直接伪造这两个头；本地 IDE 直连时它们都不存在，
     * 自然回退到连接地址。
     */
    private String clientIp(ServerHttpRequest request) {
        HttpHeaders headers = request.getHeaders();

        String realIp = headers.getFirst("X-Real-IP");
        if (StringUtils.hasText(realIp)) {
            return realIp.trim();
        }

        String forwarded = headers.getFirst("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            int comma = forwarded.indexOf(',');
            return (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
        }

        InetSocketAddress remote = request.getRemoteAddress();
        if (remote != null && remote.getAddress() != null) {
            return remote.getAddress().getHostAddress();
        }
        return UNKNOWN_IP;
    }
}
