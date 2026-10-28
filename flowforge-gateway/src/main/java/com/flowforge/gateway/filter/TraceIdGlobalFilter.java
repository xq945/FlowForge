package com.flowforge.gateway.filter;

import com.flowforge.common.api.TraceContext;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 链路追踪 ID 注入。
 *
 * <p>必须最先执行：后续的鉴权失败响应也要带上 traceId。
 * 注意 WebFlux 下请求会跨线程调度，MDC 不保证透传，因此这里只负责 header 透传；
 * 业务服务的 Servlet Filter 负责把 traceId 放进 MDC 供日志使用。
 */
@Component
public class TraceIdGlobalFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String traceId = exchange.getRequest().getHeaders().getFirst(TraceContext.HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = TraceContext.newTraceId();
        }
        final String effectiveTraceId = traceId;

        exchange.getResponse().getHeaders().set(TraceContext.HEADER, effectiveTraceId);
        ServerHttpRequest request = exchange.getRequest().mutate()
                .header(TraceContext.HEADER, effectiveTraceId)
                .build();
        return chain.filter(exchange.mutate().request(request).build());
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
