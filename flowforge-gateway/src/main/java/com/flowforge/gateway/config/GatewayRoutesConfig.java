package com.flowforge.gateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 路由声明。
 *
 * <p>用 Java DSL 而不是 YAML：Gateway 4.2 起引入了新的配置前缀，
 * 硬编码 YAML 前缀在不同小版本间容易失效，而 DSL 由编译期保证正确。
 *
 * <p>统一对外前缀为 {@code /api}，转发前剥掉该前缀，
 * 业务服务的 Controller 路径从根开始，不感知网关的存在。
 */
@Configuration
public class GatewayRoutesConfig {

    private static final String USER_SERVICE = "flowforge-user-service";

    @Bean
    public RouteLocator flowForgeRoutes(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("user-service", route -> route
                        .path("/api/auth/**", "/api/users/**")
                        .filters(filter -> filter
                                .stripPrefix(1)
                                // 网关与下游服务都会写 X-Trace-Id，合并后会出现两个同名头
                                .dedupeResponseHeader("X-Trace-Id", "RETAIN_FIRST"))
                        .uri("lb://" + USER_SERVICE))
                .build();
    }
}
