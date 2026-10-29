package com.flowforge.gateway.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowforge.common.api.ApiResponse;
import com.flowforge.common.api.AuthHeaders;
import com.flowforge.common.api.ErrorCode;
import com.flowforge.common.api.TraceContext;
import com.flowforge.gateway.config.GatewaySecurityProperties;
import com.flowforge.gateway.security.JwtVerifier;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;

/**
 * 网关统一认证。
 *
 * <p>白名单外的请求必须携带有效 JWT；校验通过后把用户身份写入内部头。
 * 这一步只是**快速失败**，业务服务仍会自行校验签名与资源归属。
 */
@Component
public class JwtAuthGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthGlobalFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtVerifier jwtVerifier;
    private final ObjectMapper objectMapper;
    private final PathMatcher pathMatcher = new AntPathMatcher();
    private final List<String> whitelist;

    public JwtAuthGlobalFilter(JwtVerifier jwtVerifier,
                               ObjectMapper objectMapper,
                               GatewaySecurityProperties properties) {
        this.jwtVerifier = jwtVerifier;
        this.objectMapper = objectMapper;
        this.whitelist = properties.getWhitelist();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if (isWhitelisted(path)) {
            return chain.filter(exchange);
        }

        String header = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return reject(exchange);
        }

        try {
            Claims claims = jwtVerifier.verify(header.substring(BEARER_PREFIX.length())).getPayload();
            ServerHttpRequest request = exchange.getRequest().mutate()
                    .header(AuthHeaders.USER_ID, claims.getSubject())
                    .header(AuthHeaders.USER_NAME, String.valueOf(claims.getOrDefault("username", "")))
                    .header(AuthHeaders.USER_ROLES, joinRoles(claims.get("roles")))
                    .build();
            ServerWebExchange authenticated = exchange.mutate().request(request).build();
            // 身份另存一份到 attribute：请求头可以被客户端伪造，白名单路径上的头
            // 也不会被这里覆盖，只有 attribute 能证明「这个用户 ID 是网关验过的」
            authenticated.getAttributes().put(GatewayAttributes.USER_ID, claims.getSubject());
            return chain.filter(authenticated);
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("JWT 校验失败 path={} reason={}", path, ex.getMessage());
            return reject(exchange);
        }
    }

    private boolean isWhitelisted(String path) {
        return whitelist.stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    private String joinRoles(Object roles) {
        if (roles instanceof Collection<?> collection) {
            return collection.stream().map(String::valueOf).reduce((a, b) -> a + "," + b).orElse("");
        }
        return "";
    }

    private Mono<Void> reject(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        // 网关没有 MDC 上下文，traceId 从 TraceIdGlobalFilter 写入的响应头取
        String traceId = response.getHeaders().getFirst(TraceContext.HEADER);
        try {
            byte[] body = objectMapper.writeValueAsBytes(
                    ApiResponse.fail(ErrorCode.UNAUTHORIZED, ErrorCode.UNAUTHORIZED.message(), traceId));
            DataBuffer buffer = response.bufferFactory().wrap(body);
            return response.writeWith(Mono.just(buffer));
        } catch (JsonProcessingException ex) {
            byte[] fallback = "{\"code\":\"A0301\",\"message\":\"unauthorized\"}".getBytes(StandardCharsets.UTF_8);
            return response.writeWith(Mono.just(response.bufferFactory().wrap(fallback)));
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
