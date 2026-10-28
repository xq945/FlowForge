package com.flowforge.execution.engine;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

/**
 * 节点执行上下文。
 *
 * @param upstreamOutput 直接前置节点中最近一次成功输出，供条件节点做比较
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NodeContext(
        Long executionId,
        Long nodeExecutionId,
        String nodeKey,
        String nodeType,
        Map<String, Object> config,
        int attemptNo,
        Map<String, Object> upstreamOutput
) {
}
