package com.flowforge.gateway.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 网关限流参数。
 *
 * <p>这些字段会被 Nacos 配置覆盖，因此必须能在运行时重绑：
 * 用普通的 {@code @ConfigurationProperties} bean（不加 {@code @RefreshScope}），
 * Spring Cloud 的 {@code ConfigurationPropertiesRebinder} 会对着同一个实例重新绑定，
 * 持有该引用的过滤器无需重建就能看到新值。
 *
 * <p>注意 {@code skipPaths} 是集合属性：配置文件里一旦出现同名字段，
 * Spring Boot 会先清空再填充，字段上的默认值会被整个覆盖。
 * 所以默认值只放「框架路径」，业务路径一律写到 yml 里。
 */
@ConfigurationProperties(prefix = "flowforge.gateway.rate-limit")
public class RateLimitProperties {

    /** 总开关，出问题时可以通过配置中心一键关闭而不重启 */
    private boolean enabled = true;

    /** 每秒向桶里补充的令牌数，即稳态 QPS */
    private int replenishRate = 20;

    /** 桶容量，即允许的突发量 */
    private int burstCapacity = 40;

    /** 每个请求消耗的令牌数 */
    private int requestedTokens = 1;

    /** 限流键的过期时间，闲置超过该时长后计数清零 */
    private Duration ttl = Duration.ofSeconds(60);

    /** 需要跳过的路径（Ant 风格） */
    private List<String> skipPaths = new ArrayList<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getReplenishRate() {
        return replenishRate;
    }

    public void setReplenishRate(int replenishRate) {
        this.replenishRate = replenishRate;
    }

    public int getBurstCapacity() {
        return burstCapacity;
    }

    public void setBurstCapacity(int burstCapacity) {
        this.burstCapacity = burstCapacity;
    }

    public int getRequestedTokens() {
        return requestedTokens;
    }

    public void setRequestedTokens(int requestedTokens) {
        this.requestedTokens = requestedTokens;
    }

    public Duration getTtl() {
        return ttl;
    }

    public void setTtl(Duration ttl) {
        this.ttl = ttl;
    }

    public List<String> getSkipPaths() {
        return skipPaths;
    }

    public void setSkipPaths(List<String> skipPaths) {
        this.skipPaths = skipPaths == null ? new ArrayList<>() : skipPaths;
    }
}
