package com.flowforge.common.web;

import com.flowforge.common.api.TraceContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 为每个请求建立 traceId 上下文。
 *
 * <p>上游（网关）传来的 traceId 直接沿用，保证跨服务可串联；
 * 没有则新建，写入响应头，方便用户报障时直接给出可检索的 ID。
 */
public class TraceIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String traceId = request.getHeader(TraceContext.HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = TraceContext.newTraceId();
        }
        TraceContext.set(traceId);
        response.setHeader(TraceContext.HEADER, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            TraceContext.clear();
        }
    }
}
