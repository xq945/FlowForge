package com.flowforge.execution.engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 可达性计算是条件分支跳过的核心：判错了会让节点该跳的没跳、
 * 或者该跑的被误判为不可达，工作流永远结束不了。
 */
class ExecutionGraphTest {

    /** fetch → judge ─TRUE→ yes ─┐
     *                  └FALSE→ no ─┴→ merge */
    private final ExecutionGraph graph = new ExecutionGraph(
            Set.of("fetch", "judge", "yes", "no", "merge"),
            List.of(
                    new ExecutionGraph.Edge("fetch", "judge", null),
                    new ExecutionGraph.Edge("judge", "yes", "TRUE"),
                    new ExecutionGraph.Edge("judge", "no", "FALSE"),
                    new ExecutionGraph.Edge("yes", "merge", null),
                    new ExecutionGraph.Edge("no", "merge", null)));

    @Test
    @DisplayName("条件节点尚未执行时，两条分支都保持可达")
    void keepsBothBranchesOpenBeforeConditionResolved() {
        Set<String> reachable = graph.reachableNodes(Map.of());

        assertThat(reachable).containsExactlyInAnyOrder("fetch", "judge", "yes", "no", "merge");
    }

    @Test
    @DisplayName("条件结果为 TRUE 时，FALSE 分支不可达")
    void closesFalseBranchWhenConditionIsTrue() {
        Set<String> reachable = graph.reachableNodes(Map.of("judge", "TRUE"));

        assertThat(reachable).contains("yes", "merge");
        assertThat(reachable).doesNotContain("no");
    }

    @Test
    @DisplayName("条件结果为 FALSE 时，TRUE 分支不可达")
    void closesTrueBranchWhenConditionIsFalse() {
        Set<String> reachable = graph.reachableNodes(Map.of("judge", "FALSE"));

        assertThat(reachable).contains("no", "merge");
        assertThat(reachable).doesNotContain("yes");
    }

    @Test
    @DisplayName("两条分支都不可达时，汇合节点也不可达")
    void mergeBecomesUnreachableWhenBothBranchesClosed() {
        // 构造一个两条入边都来自同一条件节点两个分支的汇合点
        ExecutionGraph bothClosed = new ExecutionGraph(
                Set.of("judge", "a", "b", "merge"),
                List.of(
                        new ExecutionGraph.Edge("judge", "a", "TRUE"),
                        new ExecutionGraph.Edge("judge", "b", "FALSE"),
                        new ExecutionGraph.Edge("a", "merge", null),
                        new ExecutionGraph.Edge("b", "merge", null)));

        Set<String> reachable = bothClosed.reachableNodes(Map.of("judge", "TRUE"));

        assertThat(reachable).contains("a", "merge").doesNotContain("b");
        assertThat(reachable).contains("judge");
    }

    @Test
    @DisplayName("多起点：每个入度为 0 的节点都是起点")
    void treatsEveryZeroInDegreeNodeAsRoot() {
        ExecutionGraph parallel = new ExecutionGraph(
                Set.of("a", "b", "merge"),
                List.of(
                        new ExecutionGraph.Edge("a", "merge", null),
                        new ExecutionGraph.Edge("b", "merge", null)));

        assertThat(parallel.reachableNodes(Map.of())).containsExactlyInAnyOrder("a", "b", "merge");
    }

    @Test
    @DisplayName("前置节点按入边反查")
    void listsPredecessors() {
        assertThat(graph.predecessorsOf("merge")).containsExactlyInAnyOrder("yes", "no");
        assertThat(graph.predecessorsOf("fetch")).isEmpty();
    }

    @Test
    @DisplayName("下游节点包含全部可达后代，不含自身")
    void listsDescendants() {
        assertThat(graph.descendantsOf("fetch")).containsExactlyInAnyOrder("judge", "yes", "no", "merge");
        assertThat(graph.descendantsOf("yes")).containsExactly("merge");
        assertThat(graph.descendantsOf("merge")).isEmpty();
    }

    @Test
    @DisplayName("空图不抛异常")
    void handlesEmptyGraph() {
        ExecutionGraph empty = new ExecutionGraph(Set.of(), List.of());

        assertThat(empty.reachableNodes(Map.of())).isEmpty();
        assertThat(empty.descendantsOf("anything")).isEmpty();
    }
}
