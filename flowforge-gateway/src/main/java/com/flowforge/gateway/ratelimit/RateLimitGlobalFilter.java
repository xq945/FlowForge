package com.flowforge.gateway.ratelimit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowforge.common.api.ApiResponse;
import com.flowforge.common.api.ErrorCode;
import com.flowforge.common.api.TraceContext;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 网关限流。
 *
 * <p>排在 {@code JwtAuthGlobalFilter} 之后，这样已认证请求按用户 ID 计数，
 * 匿名请求（登录、注册）按客户端 IP 计数。
 *
 * <p>不用 Spring Cloud Gateway 自带的 {@code RequestRateLimiter}：它拒绝时只是
 * 把响应标记完成，body 为空，套不上项目统一的 {@code ApiResponse} 结构。
 */
@Component
public class RateLimitGlobalFilter implements GlobalFilter, Ordered {

    private static final String ACTUATOR_PATH = "/actuator";
    private static final String ACTUATOR_PREFIX = "/actuator/";
    private static final String HEADER_LIMIT = "X-RateLimit-Limit";
    private static final String HEADER_REMAINING = "X-RateLimit-Remaining";

    private static final PathMatcher PATH_MATCHER = new AntPathMatcher();

    private final RateLimitProperties properties;
    private final RateLimitKeyResolver keyResolver;
    private final TokenBucketRateLimiter rateLimiter;
    private final ObjectMapper objectMapper;

    public RateLimitGlobalFilter(RateLimitProperties properties,
                                 RateLimitKeyResolver keyResolver,
                                 TokenBucketRateLimiter rateLimiter,
                                 ObjectMapper objectMapper) {
        this.properties = properties;
        this.keyResolver = keyResolver;
        this.rateLimiter = rateLimiter;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!properties.isEnabled() || shouldSkip(exchange.getRequest().getURI().getPath())) {
            return chain.filter(exchange);
        }
        // 配置中心误填 0 或负数时宁可漏限流，也不能把所有请求都拒掉
        if (!isSane()) {
            return chain.filter(exchange);
        }

        String key = keyResolver.resolve(exchange);

        // 令牌桶内部已把 Redis 异常与超时兜成「放行」，这里的 flatMap 只管编排响应，
        // 下游链路的异常不会被误判成限流故障
        return rateLimiter.tryConsume(key,
                        properties.getReplenishRate(),
                        properties.getBurstCapacity(),
                        properties.getRequestedTokens(),
                        properties.getTtl())
                .flatMap(decision -> decision.allowed()
                        ? allow(exchange, chain, decision)
                        : reject(exchange, decision));
    }

    private boolean isSane() {
        return properties.getReplenishRate() > 0
                && properties.getBurstCapacity() > 0
                && properties.getRequestedTokens() > 0;
    }

    private boolean shouldSkip(String path) {
        if (ACTUATOR_PATH.equals(path) || path.startsWith(ACTUATOR_PREFIX)) {
            return true;
        }
        List<String> skipPaths = properties.getSkipPaths();
        return skipPaths != null && skipPaths.stream().anyMatch(pattern -> PATH_MATCHER.match(pattern, path));
    }

    private Mono<Void> allow(ServerWebExchange exchange, GatewayFilterChain chain, RateLimitDecision decision) {
        if (decision.remaining() >= 0) {
            ServerHttpResponse response = exchange.getResponse();
            response.getHeaders().set(HEADER_LIMIT, String.valueOf(properties.getBurstCapacity()));
            response.getHeaders().set(HEADER_REMAINING, String.valueOf(decision.remaining()));
        }
        return chain.filter(exchange);
    }

    private Mono<Void> reject(ServerWebExchange exchange, RateLimitDecision decision) {
        ServerHttpResponse response = exchange.getResponse();
        long retryAfterSeconds = Math.max(1, (decision.retryAfterMs() + 999) / 1000);

        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        response.getHeaders().set(HEADER_LIMIT, String.valueOf(properties.getBurstCapacity()));
        response.getHeaders().set(HEADER_REMAINING, "0");
        response.getHeaders().set(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));

        // 网关没有 MDC 上下文，traceId 只能从 TraceIdGlobalFilter 写好的响应头里取
        String traceId = response.getHeaders().getFirst(TraceContext.HEADER);
        String message = ErrorCode.RATE_LIMITED.message() + "（" + retryAfterSeconds + " 秒后可重试）";
        try {
            byte[] body = objectMapper.writeValueAsBytes(
                    ApiResponse.fail(ErrorCode.RATE_LIMITED, message, traceId));
            return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
        } catch (JsonProcessingException ex) {
            byte[] fallback = "{\"code\":\"A0429\",\"message\":\"rate limited\"}".getBytes(StandardCharsets.UTF_8);
            return response.writeWith(Mono.just(response.bufferFactory().wrap(fallback)));
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 20;
    }
}
