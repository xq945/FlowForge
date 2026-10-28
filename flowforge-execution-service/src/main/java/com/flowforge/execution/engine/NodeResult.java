package com.flowforge.execution.engine;

import java.util.Map;

/**
 * 节点一次执行的结果。
 *
 * <p>{@code retryable} 与 {@code uncertain} 是分开的两个概念：
 * 前者表示「重试有可能成功」，后者表示「结果未知，对端可能已经执行了」。
 * 读超时同时满足两者，但默认策略是**不自动重试**——宁可停下来让人确认，
 * 也不要贸然制造第二次外部副作用。
 */
public record NodeResult(
        boolean success,
        boolean retryable,
        boolean uncertain,
        Map<String, Object> output,
        String errorCode,
        String errorMessage,
        String requestSummary,
        String responseSummary
) {

    public static NodeResult success(Map<String, Object> output, String requestSummary, String responseSummary) {
        return new NodeResult(true, false, false, output, null, null, requestSummary, responseSummary);
    }

    public static NodeResult failure(String errorCode, String errorMessage,
                                     boolean retryable, boolean uncertain,
                                     String requestSummary, String responseSummary) {
        return new NodeResult(false, retryable, uncertain, null, errorCode, errorMessage,
                requestSummary, responseSummary);
    }
}
