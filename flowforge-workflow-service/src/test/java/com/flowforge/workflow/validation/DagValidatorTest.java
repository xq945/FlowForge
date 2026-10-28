package com.flowforge.workflow.validation;

import com.flowforge.common.contract.EdgeSpec;
import com.flowforge.common.contract.NodeSpec;
import com.flowforge.workflow.domain.ValidationResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DagValidatorTest {

    private final DagValidator validator = new DagValidator();

    private static NodeSpec http(String key) {
        return new NodeSpec(key, "HTTP", key, Map.of("url", "https://example.com/api", "method", "GET"),
                null, null, null, null, null, null, null);
    }

    private static NodeSpec mock(String key) {
        return new NodeSpec(key, "MOCK_TASK", key, Map.of("durationMs", 100),
                null, null, null, null, null, null, null);
    }

    private static NodeSpec condition(String key) {
        return new NodeSpec(key, "CONDITION", key,
                Map.of("field", "status", "operator", "EQ", "value", "ok"),
                null, null, null, null, null, null, null);
    }

    private static EdgeSpec edge(String from, String to) {
        return new EdgeSpec(from, to, null);
    }

    private static EdgeSpec branch(String from, String to, String flag) {
        return new EdgeSpec(from, to, flag);
    }

    @Test
    @DisplayName("线性工作流通过校验")
    void acceptsLinearWorkflow() {
        ValidationResult result = validator.validate(
                List.of(http("a"), mock("b")),
                List.of(edge("a", "b")));

        assertThat(result.valid()).isTrue();
    }

    @Test
    @DisplayName("条件节点两条分支完整时通过校验")
    void acceptsConditionBranches() {
        ValidationResult result = validator.validate(
                List.of(http("start"), condition("judge"), mock("yes"), mock("no")),
                List.of(
                        edge("start", "judge"),
                        branch("judge", "yes", "TRUE"),
                        branch("judge", "no", "FALSE")));

        assertThat(result.valid()).isTrue();
    }

    @Test
    @DisplayName("循环依赖被拒绝")
    void rejectsCycle() {
        ValidationResult result = validator.validate(
                List.of(mock("a"), mock("b"), mock("c")),
                List.of(edge("a", "b"), edge("b", "c"), edge("c", "a")));

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.contains("循环依赖"));
    }

    @Test
    @DisplayName("指向不存在节点的边被拒绝")
    void rejectsDanglingEdge() {
        ValidationResult result = validator.validate(
                List.of(mock("a")),
                List.of(edge("a", "ghost")));

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.contains("终点不存在"));
    }

    @Test
    @DisplayName("节点 key 重复被拒绝")
    void rejectsDuplicateKey() {
        ValidationResult result = validator.validate(
                List.of(mock("a"), mock("a")),
                List.of());

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.contains("重复"));
    }

    @Test
    @DisplayName("条件节点缺少 FALSE 出边被拒绝")
    void rejectsIncompleteConditionEdges() {
        ValidationResult result = validator.validate(
                List.of(condition("judge"), mock("yes")),
                List.of(branch("judge", "yes", "TRUE")));

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.contains("TRUE 与 FALSE"));
    }

    @Test
    @DisplayName("非条件节点的出边不能带分支标记")
    void rejectsBranchFlagOnNormalNode() {
        ValidationResult result = validator.validate(
                List.of(http("a"), mock("b")),
                List.of(branch("a", "b", "TRUE")));

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.contains("不能带 TRUE/FALSE"));
    }

    @Test
    @DisplayName("孤立节点（既无入边也无出边）被拒绝")
    void rejectsOrphanNode() {
        ValidationResult result = validator.validate(
                List.of(http("a"), mock("b"), mock("orphan")),
                List.of(edge("a", "b")));

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.contains("没有任何依赖关系"));
    }

    @Test
    @DisplayName("多个起始节点（并行分支）是合法的")
    void acceptsMultipleRoots() {
        ValidationResult result = validator.validate(
                List.of(mock("a"), mock("b"), mock("merge")),
                List.of(edge("a", "merge"), edge("b", "merge")));

        assertThat(result.valid()).isTrue();
    }

    @Test
    @DisplayName("非 http/https 的 URL 被拒绝")
    void rejectsNonHttpUrl() {
        NodeSpec bad = new NodeSpec("a", "HTTP", "a", Map.of("url", "file:///etc/passwd"),
                null, null, null, null, null, null, null);

        ValidationResult result = validator.validate(List.of(bad), List.of());

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.contains("只支持 http/https"));
    }

    @Test
    @DisplayName("草稿模式跳过拓扑校验，允许保存中间态")
    void draftModeSkipsTopologyChecks() {
        // 有环、且存在孤立节点，但结构本身合法
        ValidationResult result = validator.validate(
                List.of(mock("a"), mock("b"), mock("orphan")),
                List.of(edge("a", "b"), edge("b", "a")),
                false);

        assertThat(result.valid()).isTrue();
    }

    @Test
    @DisplayName("草稿模式仍拒绝结构错误")
    void draftModeStillRejectsStructuralErrors() {
        ValidationResult result = validator.validate(
                List.of(mock("a")),
                List.of(edge("a", "ghost")),
                false);

        assertThat(result.valid()).isFalse();
    }
}
