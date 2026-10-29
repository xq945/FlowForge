package com.flowforge.gateway.ratelimit;

/**
 * 一次限流判断的结果。
 *
 * <p>{@code remaining} 为负数表示「没拿到 Redis 结果」（放行兜底），
 * 此时不写 {@code X-RateLimit-*} 响应头——那会向客户端谎报一个配额。
 */
public record RateLimitDecision(boolean allowed, long remaining, long retryAfterMs) {

    public static RateLimitDecision allow() {
        return new RateLimitDecision(true, -1, 0);
    }
}
