package com.flowforge.execution.executor;

import com.flowforge.execution.engine.NodeContext;
import com.flowforge.execution.engine.NodeResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ConditionNodeExecutorTest {

    private final ConditionNodeExecutor executor = new ConditionNodeExecutor();

    private NodeResult run(Map<String, Object> config, Map<String, Object> upstream) {
        return executor.execute(new NodeContext(1L, 2L, "judge", "CONDITION", config, 1, upstream));
    }

    private Map<String, Object> upstreamWithStatus(String status) {
        return Map.of("body", Map.of("status", status));
    }

    @Test
    @DisplayName("支持点号路径从上游输出中取值")
    void readsNestedField() {
        NodeResult result = run(
                Map.of("field", "body.status", "operator", "EQ", "value", "UP"),
                upstreamWithStatus("UP"));

        assertThat(result.success()).isTrue();
        assertThat(result.output().get("result")).isEqualTo(true);
    }

    @Test
    @DisplayName("EQ 不匹配时结果为 false，但节点本身执行成功")
    void mismatchIsStillSuccess() {
        NodeResult result = run(
                Map.of("field", "body.status", "operator", "EQ", "value", "DOWN"),
                upstreamWithStatus("UP"));

        assertThat(result.success()).isTrue();
        assertThat(result.output().get("result")).isEqualTo(false);
    }

    @Test
    @DisplayName("数值比较：GT / LE")
    void comparesNumbers() {
        assertThat(run(Map.of("field", "count", "operator", "GT", "value", 5),
                Map.of("count", 10)).output().get("result")).isEqualTo(true);

        assertThat(run(Map.of("field", "count", "operator", "LE", "value", 5),
                Map.of("count", 10)).output().get("result")).isEqualTo(false);
    }

    @Test
    @DisplayName("字符串数字也按数值比较")
    void comparesNumericStringsAsNumbers() {
        NodeResult result = run(
                Map.of("field", "amount", "operator", "GT", "value", 100),
                Map.of("amount", "250"));

        assertThat(result.output().get("result")).isEqualTo(true);
    }

    @Test
    @DisplayName("CONTAINS 与 EMPTY")
    void supportsContainsAndEmpty() {
        assertThat(run(Map.of("field", "msg", "operator", "CONTAINS", "value", "ok"),
                Map.of("msg", "all ok")).output().get("result")).isEqualTo(true);

        assertThat(run(Map.of("field", "missing", "operator", "EMPTY", "value", ""),
                Map.of()).output().get("result")).isEqualTo(true);
    }

    @Test
    @DisplayName("字段不存在时视为空值")
    void missingFieldIsTreatedAsNull() {
        NodeResult result = run(
                Map.of("field", "body.not_there", "operator", "EQ", "value", "UP"),
                upstreamWithStatus("UP"));

        assertThat(result.success()).isTrue();
        assertThat(result.output().get("result")).isEqualTo(false);
    }

    @Test
    @DisplayName("对非数字做数值比较属于配置错误，不可重试")
    void nonNumericComparisonFailsWithoutRetry() {
        NodeResult result = run(
                Map.of("field", "body.status", "operator", "GT", "value", 1),
                upstreamWithStatus("UP"));

        assertThat(result.success()).isFalse();
        assertThat(result.errorCode()).isEqualTo("CONDITION_EVAL_ERROR");
        assertThat(result.retryable()).isFalse();
    }

    @Test
    @DisplayName("未知操作符属于配置错误")
    void unknownOperatorFails() {
        NodeResult result = run(
                Map.of("field", "body.status", "operator", "LIKE", "value", "UP"),
                upstreamWithStatus("UP"));

        assertThat(result.success()).isFalse();
        assertThat(result.retryable()).isFalse();
    }
}
