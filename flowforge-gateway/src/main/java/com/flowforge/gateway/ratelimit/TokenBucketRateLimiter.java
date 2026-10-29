package com.flowforge.gateway.ratelimit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

/**
 * 基于 Redis 的令牌桶。
 *
 * <p>把「用 Redis 算一次令牌桶」单独拆出来，和「拿到结果后怎么响应」分开：
 * 计数器在 Redis 里共享，网关扩容时总配额不变，不需要按实例数折算。
 *
 * <p><b>Redis 故障一律放行</b>。限流是保护手段而不是业务前置条件，
 * 它坏掉不应该变成请求失败的原因——这属于「失败了也不影响正确性」的那类依赖。
 */
@Component
public class TokenBucketRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(TokenBucketRateLimiter.class);

    /**
     * 独立于 spring.data.redis.timeout（2s）的短超时。
     * Redis 挂掉时不能让每个请求都白等 2 秒，否则限流器本身就是一次雪崩。
     */
    private static final Duration TIMEOUT = Duration.ofMillis(200);

    private final ReactiveStringRedisTemplate redisTemplate;
    private final RedisScript<List> script;

    public TokenBucketRateLimiter(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        DefaultRedisScript<List> tokenBucket = new DefaultRedisScript<>();
        tokenBucket.setLocation(new ClassPathResource("lua/token_bucket.lua"));
        tokenBucket.setResultType(List.class);
        this.script = tokenBucket;
    }

    public Mono<RateLimitDecision> tryConsume(String key,
                                              int replenishRate,
                                              int burstCapacity,
                                              int requestedTokens,
                                              Duration ttl) {
        return redisTemplate
                .execute(script, List.of(key),
                        String.valueOf(replenishRate),
                        String.valueOf(burstCapacity),
                        String.valueOf(requestedTokens),
                        String.valueOf(Math.max(1, ttl.toSeconds())))
                .next()
                .timeout(TIMEOUT)
                .map(result -> toDecision((List<?>) result))
                .defaultIfEmpty(RateLimitDecision.allow())
                .onErrorResume(ex -> {
                    log.warn("限流检查失败，放行请求 key={} reason={}", key, ex.toString());
                    return Mono.just(RateLimitDecision.allow());
                });
    }

    private static RateLimitDecision toDecision(List<?> result) {
        if (result == null || result.size() < 3) {
            return RateLimitDecision.allow();
        }
        return new RateLimitDecision(
                toLong(result.get(0)) == 1L,
                toLong(result.get(1)),
                toLong(result.get(2)));
    }

    private static long toLong(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }
}
