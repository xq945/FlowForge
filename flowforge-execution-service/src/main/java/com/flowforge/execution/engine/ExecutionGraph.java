package com.flowforge.execution.engine;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 执行期的图结构与可达性计算。
 *
 * <p>条件分支的跳过逻辑采用**全局重算**而不是增量传播：每次节点完成后，
 * 基于「已确定的条件结果」重新算一遍可达集合，不可达的节点直接判为 SKIPPED。
 * 这样无论嵌套几层条件、汇合点有几个，规则都只有一条，不存在增量状态出错的可能。
 *
 * <p>纯逻辑、无外部依赖，便于单元测试。
 */
public final class ExecutionGraph {

    /**
     * @param condition 仅条件节点的出边非空，取值 TRUE / FALSE
     */
    public record Edge(String from, String to, String condition) {
    }

    private final Set<String> nodeKeys;
    private final List<Edge> edges;

    public ExecutionGraph(Set<String> nodeKeys, List<Edge> edges) {
        this.nodeKeys = Set.copyOf(nodeKeys);
        this.edges = List.copyOf(edges);
    }

    /**
     * 计算从起始节点出发、沿「当前仍可能通行」的边能到达的全部节点。
     *
     * @param resolvedConditions 已执行完成的条件节点 → 结果（TRUE / FALSE）。
     *                           尚未执行的条件节点视为「两条分支都可能」，因此其下游暂时保持可达，
     *                           等它执行完再收敛。
     */
    public Set<String> reachableNodes(Map<String, String> resolvedConditions) {
        Set<String> hasIncoming = new HashSet<>();
        for (Edge edge : edges) {
            hasIncoming.add(edge.to());
        }

        Map<String, List<Edge>> outgoing = new HashMap<>();
        for (Edge edge : edges) {
            outgoing.computeIfAbsent(edge.from(), key -> new ArrayList<>()).add(edge);
        }

        Set<String> visited = new HashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        for (String key : nodeKeys) {
            if (!hasIncoming.contains(key)) {
                queue.add(key);
            }
        }

        while (!queue.isEmpty()) {
            String current = queue.poll();
            if (!visited.add(current)) {
                continue;
            }
            for (Edge edge : outgoing.getOrDefault(current, List.of())) {
                if (isEdgeOpen(edge, resolvedConditions)) {
                    queue.add(edge.to());
                }
            }
        }
        return visited;
    }

    /** 某节点的直接前置节点。 */
    public List<String> predecessorsOf(String nodeKey) {
        return edges.stream()
                .filter(edge -> edge.to().equals(nodeKey))
                .map(Edge::from)
                .toList();
    }

    /** 某节点沿出边可达的全部下游节点（不含自身），用于失败时的终止传播。 */
    public Set<String> descendantsOf(String nodeKey) {
        Map<String, List<Edge>> outgoing = new HashMap<>();
        for (Edge edge : edges) {
            outgoing.computeIfAbsent(edge.from(), key -> new ArrayList<>()).add(edge);
        }

        Set<String> visited = new HashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        queue.add(nodeKey);
        while (!queue.isEmpty()) {
            String current = queue.poll();
            for (Edge edge : outgoing.getOrDefault(current, List.of())) {
                if (visited.add(edge.to())) {
                    queue.add(edge.to());
                }
            }
        }
        return visited;
    }

    private boolean isEdgeOpen(Edge edge, Map<String, String> resolvedConditions) {
        if (edge.condition() == null || edge.condition().isBlank()) {
            return true;
        }
        // 条件节点尚未执行：两条分支都保留，避免过早把节点判死
        String result = resolvedConditions.get(edge.from());
        if (result == null) {
            return true;
        }
        return result.equalsIgnoreCase(edge.condition());
    }
}
