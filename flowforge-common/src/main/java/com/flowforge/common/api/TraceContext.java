package com.flowforge.common.api;

import org.slf4j.MDC;

import java.util.UUID;

/**
 * 链路追踪 ID 的线程上下文。
 *
 * <p>由入口过滤器写入 MDC，日志 pattern 直接引用 {@code %X{traceId}}，
 * 业务代码不需要手工传递，响应体与响应头也会带上。
 */
public final class TraceContext {

    public static final String HEADER = "X-Trace-Id";
    public static final String MDC_KEY = "traceId";

    private TraceContext() {
    }

    public static String currentTraceId() {
        return MDC.get(MDC_KEY);
    }

    public static void set(String traceId) {
        MDC.put(MDC_KEY, traceId);
    }

    public static void clear() {
        MDC.remove(MDC_KEY);
    }

    public static String newTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
