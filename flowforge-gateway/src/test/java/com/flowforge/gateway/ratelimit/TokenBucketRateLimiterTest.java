package com.flowforge.gateway.ratelimit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lua 令牌桶的行为验证。
 *
 * <p>脚本逻辑无法用 mock 验证，这里起一个真实 Redis 跑脚本：
 * 突发限额、按时间补充、键之间互相隔离。
 */
@Testcontainers(disabledWithoutDocker = true)
class TokenBucketRateLimiterTest {

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    private static final Duration TTL = Duration.ofSeconds(30);

    private static LettuceConnectionFactory connectionFactory;
    private static TokenBucketRateLimiter limiter;

    @BeforeAll
    static void setUp() {
        connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        limiter = new TokenBucketRateLimiter(new ReactiveStringRedisTemplate(connectionFactory));
    }

    @AfterAll
    static void tearDown() {
        connectionFactory.destroy();
    }

    @Test
    @DisplayName("容量内全部放行，耗尽后拒绝并给出等待时间")
    void rejectsAfterBurst() {
        String key = uniqueKey("burst");

        for (int i = 0; i < 20; i++) {
            assertThat(allow(key, 1, 20)).as("第 %d 次请求", i + 1).isTrue();
        }

        RateLimitDecision denied = limiter.tryConsume(key, 1, 20, 1, TTL).block();
        assertThat(denied).isNotNull();
        assertThat(denied.allowed()).isFalse();
        assertThat(denied.remaining()).isZero();
        assertThat(denied.retryAfterMs()).isPositive();
    }

    @Test
    @DisplayName("等待后按补充速率恢复")
    void refillsOverTime() throws InterruptedException {
        String key = uniqueKey("refill");
        // 每秒补 100 个、容量 1：耗尽后约 10ms 就能再通过一次
        assertThat(allow(key, 100, 1)).isTrue();
        assertThat(allow(key, 100, 1)).isFalse();

        Thread.sleep(120);

        assertThat(allow(key, 100, 1)).isTrue();
    }

    @Test
    @DisplayName("不同限流键互不影响")
    void keysAreIsolated() {
        String exhausted = uniqueKey("isolated-a");
        String fresh = uniqueKey("isolated-b");

        assertThat(allow(exhausted, 1, 1)).isTrue();
        assertThat(allow(exhausted, 1, 1)).isFalse();
        assertThat(allow(fresh, 1, 1)).isTrue();
    }

    @Test
    @DisplayName("剩余令牌数随消耗递减")
    void reportsRemainingTokens() {
        String key = uniqueKey("remaining");

        RateLimitDecision first = limiter.tryConsume(key, 1, 5, 1, TTL).block();
        RateLimitDecision second = limiter.tryConsume(key, 1, 5, 1, TTL).block();

        assertThat(first).isNotNull();
        assertThat(second).isNotNull();
        assertThat(first.remaining()).isEqualTo(4);
        assertThat(second.remaining()).isEqualTo(3);
    }

    private static boolean allow(String key, int replenishRate, int burstCapacity) {
        RateLimitDecision decision = limiter.tryConsume(key, replenishRate, burstCapacity, 1, TTL).block();
        assertThat(decision).isNotNull();
        return decision.allowed();
    }

    private static String uniqueKey(String scene) {
        return "rl:test:" + scene + ":" + UUID.randomUUID();
    }
}
