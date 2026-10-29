package com.flowforge.gateway.ratelimit;

import com.flowforge.gateway.filter.GatewayAttributes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import java.net.InetSocketAddress;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitKeyResolverTest {

    private final RateLimitKeyResolver resolver = new RateLimitKeyResolver();

    @Test
    @DisplayName("已认证请求按用户 ID 计数")
    void resolvesByUserId() {
        MockServerWebExchange exchange = exchange(MockServerHttpRequest.get("/api/workflows").build());
        exchange.getAttributes().put(GatewayAttributes.USER_ID, "42");

        assertThat(resolver.resolve(exchange)).isEqualTo("rl:user:42");
    }

    @Test
    @DisplayName("伪造的 X-User-Id 请求头不参与计数")
    void ignoresSpoofedUserIdHeader() {
        MockServerWebExchange exchange = exchange(MockServerHttpRequest.get("/api/auth/login")
                .header("X-User-Id", "999")
                .header("X-Real-IP", "203.0.113.7")
                .build());

        assertThat(resolver.resolve(exchange)).isEqualTo("rl:ip:203.0.113.7");
    }

    @Test
    @DisplayName("X-Real-IP 优先于 X-Forwarded-For")
    void prefersRealIpOverForwardedFor() {
        MockServerWebExchange exchange = exchange(MockServerHttpRequest.get("/api/auth/login")
                .header("X-Real-IP", "203.0.113.7")
                .header("X-Forwarded-For", "198.51.100.1, 10.0.0.5")
                .build());

        assertThat(resolver.resolve(exchange)).isEqualTo("rl:ip:203.0.113.7");
    }

    @Test
    @DisplayName("X-Forwarded-For 取第一段")
    void takesFirstForwardedForHop() {
        MockServerWebExchange exchange = exchange(MockServerHttpRequest.get("/api/auth/login")
                .header("X-Forwarded-For", "198.51.100.1, 10.0.0.5")
                .build());

        assertThat(resolver.resolve(exchange)).isEqualTo("rl:ip:198.51.100.1");
    }

    @Test
    @DisplayName("没有转发头时回退到连接地址")
    void fallsBackToRemoteAddress() {
        MockServerWebExchange exchange = exchange(MockServerHttpRequest.get("/api/auth/login")
                .remoteAddress(new InetSocketAddress("198.51.100.9", 12345))
                .build());

        assertThat(resolver.resolve(exchange)).isEqualTo("rl:ip:198.51.100.9");
    }

    @Test
    @DisplayName("拿不到地址时用固定的 unknown 键，避免空键打穿 Redis")
    void usesUnknownWhenNoAddress() {
        MockServerWebExchange exchange = exchange(MockServerHttpRequest.get("/api/auth/login").build());

        assertThat(resolver.resolve(exchange)).isEqualTo("rl:ip:unknown");
    }

    private static MockServerWebExchange exchange(MockServerHttpRequest request) {
        return MockServerWebExchange.from(request);
    }
}
