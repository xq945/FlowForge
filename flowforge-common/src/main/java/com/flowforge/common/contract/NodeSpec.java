package com.flowforge.common.contract;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

/**
 * 节点定义（请求与快照共用的表示）。
 *
 * <p>重试与超时的默认值在 DAG 校验阶段补齐，落库与下发执行时都使用补齐后的值，
 * 避免执行服务再去猜默认值。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NodeSpec(
        String nodeKey,
        String nodeType,
        String name,
        Map<String, Object> config,
        Integer timeoutMs,
        Integer retryMax,
        Integer retryInitialMs,
        Integer retryMaxMs,
        Boolean retryOnUncertain,
        Integer posX,
        Integer posY
) {

    public static final int DEFAULT_TIMEOUT_MS = 30_000;
    public static final int DEFAULT_RETRY_MAX = 0;
    public static final int DEFAULT_RETRY_INITIAL_MS = 1_000;
    public static final int DEFAULT_RETRY_MAX_MS = 60_000;

    public int timeoutOrDefault() {
        return timeoutMs == null || timeoutMs <= 0 ? DEFAULT_TIMEOUT_MS : timeoutMs;
    }

    public int retryMaxOrDefault() {
        return retryMax == null || retryMax < 0 ? DEFAULT_RETRY_MAX : retryMax;
    }

    public int retryInitialOrDefault() {
        return retryInitialMs == null || retryInitialMs <= 0 ? DEFAULT_RETRY_INITIAL_MS : retryInitialMs;
    }

    public int retryMaxDelayOrDefault() {
        return retryMaxMs == null || retryMaxMs <= 0 ? DEFAULT_RETRY_MAX_MS : retryMaxMs;
    }

    public boolean retryOnUncertainOrDefault() {
        return Boolean.TRUE.equals(retryOnUncertain);
    }
}
