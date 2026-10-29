package com.flowforge.gateway.ratelimit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowforge.common.api.ErrorCode;
import com.flowforge.common.api.TraceContext;
import com.flowforge.gateway.filter.GatewayAttributes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RateLimitGlobalFilterTest {

    private final TokenBucketRateLimiter rateLimiter = mock(TokenBucketRateLimiter.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RecordingChain chain = new RecordingChain();

    private RateLimitProperties properties;
    private RateLimitGlobalFilter filter;

    @BeforeEach
    void setUp() {
        properties = new RateLimitProperties();
        filter = new RateLimitGlobalFilter(properties, new RateLimitKeyResolver(), rateLimiter, objectMapper);
    }

    @Test
    @DisplayName("令牌充足时放行并回填剩余配额")
    void allowsWhenTokensAvailable() {
        when(rateLimiter.tryConsume(anyString(), anyInt(), anyInt(), anyInt(), any(Duration.class)))
                .thenReturn(Mono.just(new RateLimitDecision(true, 39, 0)));

        MockServerWebExchange exchange = exchange("/api/workflows");
        filter.filter(exchange, chain).block();

        assertThat(chain.invoked).isTrue();
        assertThat(exchange.getResponse().getHeaders().getFirst("X-RateLimit-Limit")).isEqualTo("40");
        assertThat(exchange.getResponse().getHeaders().getFirst("X-RateLimit-Remaining")).isEqualTo("39");
    }

    @Test
    @DisplayName("令牌耗尽时返回 429 与统一错误码")
    void rejectsWithUnifiedBody() throws Exception {
        when(rateLimiter.tryConsume(anyString(), anyInt(), anyInt(), anyInt(), any(Duration.class)))
                .thenReturn(Mono.just(new RateLimitDecision(false, 0, 1200)));

        MockServerWebExchange exchange = exchange("/api/workflows");
        exchange.getResponse().getHeaders().set(TraceContext.HEADER, "trace-123");
        filter.filter(exchange, chain).block();

        assertThat(chain.invoked).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(exchange.getResponse().getHeaders().getFirst("Retry-After")).isEqualTo("2");

        JsonNode body = objectMapper.readTree(exchange.getResponse().getBodyAsString().block());
        assertThat(body.get("code").asText()).isEqualTo(ErrorCode.RATE_LIMITED.code());
        assertThat(body.get("traceId").asText()).isEqualTo("trace-123");
    }

    @Test
    @DisplayName("开关关闭时完全不碰 Redis")
    void bypassesWhenDisabled() {
        properties.setEnabled(false);

        MockServerWebExchange exchange = exchange("/api/workflows");
        filter.filter(exchange, chain).block();

        assertThat(chain.invoked).isTrue();
        verify(rateLimiter, never()).tryConsume(anyString(), anyInt(), anyInt(), anyInt(), any(Duration.class));
    }

    @Test
    @DisplayName("健康检查不限流")
    void skipsActuator() {
        MockServerWebExchange exchange = exchange("/actuator/health");
        filter.filter(exchange, chain).block();

        assertThat(chain.invoked).isTrue();
        verify(rateLimiter, never()).tryConsume(anyString(), anyInt(), anyInt(), anyInt(), any(Duration.class));
    }

    @Test
    @DisplayName("阈值为 0 或负数时放行，避免误配把流量全掐死")
    void bypassesOnInsaneConfig() {
        properties.setReplenishRate(0);

        MockServerWebExchange exchange = exchange("/api/workflows");
        filter.filter(exchange, chain).block();

        assertThat(chain.invoked).isTrue();
        verify(rateLimiter, never()).tryConsume(anyString(), anyInt(), anyInt(), anyInt(), any(Duration.class));
    }

    @Test
    @DisplayName("兜底放行时不谎报配额头")
    void omitsQuotaHeadersOnFallback() {
        when(rateLimiter.tryConsume(anyString(), anyInt(), anyInt(), anyInt(), any(Duration.class)))
                .thenReturn(Mono.just(RateLimitDecision.allow()));

        MockServerWebExchange exchange = exchange("/api/workflows");
        filter.filter(exchange, chain).block();

        assertThat(chain.invoked).isTrue();
        assertThat(exchange.getResponse().getHeaders().getFirst("X-RateLimit-Remaining")).isNull();
    }

    @Test
    @DisplayName("已认证请求按用户 ID 计数，匿名请求按 IP")
    void usesUserKeyForAuthenticatedRequests() {
        when(rateLimiter.tryConsume(anyString(), anyInt(), anyInt(), anyInt(), any(Duration.class)))
                .thenReturn(Mono.just(RateLimitDecision.allow()));

        MockServerWebExchange authenticated = exchange("/api/workflows");
        authenticated.getAttributes().put(GatewayAttributes.USER_ID, "7");
        filter.filter(authenticated, chain).block();

        MockServerWebExchange anonymous = exchange("/api/auth/login");
        filter.filter(anonymous, chain).block();

        verify(rateLimiter).tryConsume(
                org.mockito.ArgumentMatchers.eq("rl:user:7"), anyInt(), anyInt(), anyInt(), any(Duration.class));
        verify(rateLimiter).tryConsume(
                org.mockito.ArgumentMatchers.eq("rl:ip:203.0.113.7"), anyInt(), anyInt(), anyInt(), any(Duration.class));
    }

    private static MockServerWebExchange exchange(String path) {
        return MockServerWebExchange.from(
                MockServerHttpRequest.get(path).header("X-Real-IP", "203.0.113.7").build());
    }

    /** 记录下游链路是否真的被执行 */
    private static final class RecordingChain implements GatewayFilterChain {

        private boolean invoked;

        @Override
        public Mono<Void> filter(ServerWebExchange exchange) {
            this.invoked = true;
            return Mono.empty();
        }
    }
}
