package com.flowforge.gateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;

/**
 * 路由声明。
 *
 * <p>用 Java DSL 而不是 YAML：Gateway 4.2 起引入了新的配置前缀，
 * 硬编码 YAML 前缀在不同小版本间容易失效，而 DSL 由编译期保证正确。
 *
 * <p>统一对外前缀为 {@code /api}，转发前剥掉该前缀，
 * 业务服务的 Controller 路径从根开始，不感知网关的存在。
 *
 * <p>注意 {@code POST /api/executions} 与 {@code GET /api/executions} 落在不同服务：
 * 前者是「触发执行」（调度），后者是「查询执行记录」（执行）。
 * 路由按声明顺序匹配，所以带 Method 谓词的那条必须排在通配路径之前。
 */
@Configuration
public class GatewayRoutesConfig {

    private static final String USER_SERVICE = "flowforge-user-service";
    private static final String WORKFLOW_SERVICE = "flowforge-workflow-service";
    private static final String SCHEDULER_SERVICE = "flowforge-scheduler-service";
    private static final String EXECUTION_SERVICE = "flowforge-execution-service";

    @Bean
    public RouteLocator flowForgeRoutes(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("user-service", route -> route
                        .path("/api/auth/**", "/api/users/**")
                        .filters(filter -> filter
                                .stripPrefix(1)
                                .dedupeResponseHeader("X-Trace-Id", "RETAIN_FIRST"))
                        .uri("lb://" + USER_SERVICE))

                .route("workflow-service", route -> route
                        .path("/api/workflows/**")
                        .filters(filter -> filter
                                .stripPrefix(1)
                                .dedupeResponseHeader("X-Trace-Id", "RETAIN_FIRST"))
                        .uri("lb://" + WORKFLOW_SERVICE))

                // 触发执行：写操作落在调度服务
                .route("execution-trigger", route -> route
                        .path("/api/executions")
                        .and()
                        .method(HttpMethod.POST)
                        .filters(filter -> filter
                                .stripPrefix(1)
                                .dedupeResponseHeader("X-Trace-Id", "RETAIN_FIRST"))
                        .uri("lb://" + SCHEDULER_SERVICE))

                // 周期任务配置：cron 管理与启停
                .route("scheduler-schedules", route -> route
                        .path("/api/schedules/**")
                        .filters(filter -> filter
                                .stripPrefix(1)
                                .dedupeResponseHeader("X-Trace-Id", "RETAIN_FIRST"))
                        .uri("lb://" + SCHEDULER_SERVICE))

                // 执行记录查询：读操作落在执行服务
                .route("execution-query", route -> route
                        .path("/api/executions", "/api/executions/**")
                        .filters(filter -> filter
                                .stripPrefix(1)
                                .dedupeResponseHeader("X-Trace-Id", "RETAIN_FIRST"))
                        .uri("lb://" + EXECUTION_SERVICE))

                .build();
    }
}
