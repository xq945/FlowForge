package com.flowforge.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 统一响应结构。
 *
 * <p>{@code code} 为 "0" 表示成功，其余见 {@link ErrorCode}。
 * 失败时不返回 data，成功时 traceId 仍会带上，便于用户报障时直接定位日志。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(String code, String message, T data, String traceId) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(ErrorCode.OK.code(), ErrorCode.OK.message(), data, TraceContext.currentTraceId());
    }

    public static ApiResponse<Void> ok() {
        return ok(null);
    }

    public static <T> ApiResponse<T> fail(ErrorCode errorCode) {
        return new ApiResponse<>(errorCode.code(), errorCode.message(), null, TraceContext.currentTraceId());
    }

    public static <T> ApiResponse<T> fail(ErrorCode errorCode, String message) {
        return new ApiResponse<>(errorCode.code(), message, null, TraceContext.currentTraceId());
    }

    /**
     * 显式指定 traceId 的失败响应。
     *
     * <p>用于 WebFlux 场景（如网关）：那里没有 MDC 上下文，
     * traceId 只存在于响应头中，需要由调用方传进来，否则响应体里的 traceId 会是空的。
     */
    public static <T> ApiResponse<T> fail(ErrorCode errorCode, String message, String traceId) {
        return new ApiResponse<>(errorCode.code(), message, null, traceId);
    }
}
