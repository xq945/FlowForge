package com.flowforge.workflow.validation;

import com.flowforge.common.contract.EdgeSpec;
import com.flowforge.common.contract.NodeSpec;
import com.flowforge.common.contract.NodeType;
import com.flowforge.workflow.domain.ValidationResult;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 工作流 DAG 静态校验。
 *
 * <p>纯函数式：只依赖传入的节点与边，不访问数据库，因此可以脱离 Spring 上下文做单元测试。
 * 校验分三层：结构（节点/边是否自洽）、拓扑（是否有环、是否连通）、配置（字段是否合法）。
 */
@Component
public class DagValidator {

    public static final int MAX_NODES = 100;
    public static final int MAX_EDGES = 500;
    private static final int MAX_ERRORS = 20;

    private static final Pattern NODE_KEY_PATTERN = Pattern.compile("^[a-zA-Z0-9_]{1,64}$");
    private static final Set<String> HTTP_METHODS = Set.of("GET", "POST", "PUT", "DELETE", "PATCH");
    private static final Set<String> CONDITION_OPERATORS =
            Set.of("EQ", "NE", "GT", "GE", "LT", "LE", "CONTAINS", "EMPTY", "NOT_EMPTY");

    /** 发布前的严格校验：结构 + 拓扑 + 配置全部通过才允许发布。 */
    public ValidationResult validate(List<NodeSpec> nodes, List<EdgeSpec> edges) {
        return validate(nodes, edges, true);
    }

    /**
     * @param strict 为 false 时只校验结构与配置（节点、边本身是否自洽），
     *               跳过环、起始节点、连通性与条件边完整性。
     *               草稿处于编辑中间态时经常还没连边，此时不应阻止保存。
     */
    public ValidationResult validate(List<NodeSpec> nodes, List<EdgeSpec> edges, boolean strict) {
        List<String> errors = new ArrayList<>();

        if (nodes == null || nodes.isEmpty()) {
            return ValidationResult.fail(List.of("工作流至少需要一个节点"));
        }
        if (nodes.size() > MAX_NODES) {
            add(errors, "节点数量不能超过 " + MAX_NODES + " 个");
        }

        Map<String, NodeSpec> byKey = new LinkedHashMap<>();
        for (NodeSpec node : nodes) {
            if (node == null) {
                add(errors, "存在空的节点定义");
                continue;
            }
            String key = node.nodeKey();
            if (key == null || !NODE_KEY_PATTERN.matcher(key).matches()) {
                add(errors, "节点 key 非法（需为 1-64 位字母、数字或下划线）：" + key);
                continue;
            }
            if (byKey.containsKey(key)) {
                add(errors, "节点 key 重复：" + key);
            }
            byKey.put(key, node);

            NodeType type = NodeType.from(node.nodeType());
            if (type == null) {
                add(errors, "节点 " + key + " 的类型非法：" + node.nodeType());
                continue;
            }
            validateConfig(key, type, node.config(), errors);
        }

        List<EdgeSpec> safeEdges = edges == null ? List.of() : edges;
        if (safeEdges.size() > MAX_EDGES) {
            add(errors, "依赖边数量不能超过 " + MAX_EDGES);
        }

        Map<String, List<EdgeSpec>> outEdges = new LinkedHashMap<>();
        Map<String, Integer> inDegree = new LinkedHashMap<>();
        for (String key : byKey.keySet()) {
            outEdges.put(key, new ArrayList<>());
            inDegree.put(key, 0);
        }

        Set<String> seenEdges = new HashSet<>();
        for (EdgeSpec edge : safeEdges) {
            if (edge == null) {
                continue;
            }
            String from = edge.fromNodeKey();
            String to = edge.toNodeKey();
            if (from == null || to == null) {
                add(errors, "依赖边缺少起点或终点");
                continue;
            }
            if (!byKey.containsKey(from)) {
                add(errors, "依赖边的起点不存在：" + from);
                continue;
            }
            if (!byKey.containsKey(to)) {
                add(errors, "依赖边的终点不存在：" + to);
                continue;
            }
            if (from.equals(to)) {
                add(errors, "不允许节点依赖自身：" + from);
                continue;
            }
            String dedupe = from + " -> " + to;
            if (!seenEdges.add(dedupe)) {
                add(errors, "重复的依赖边：" + dedupe);
                continue;
            }
            outEdges.get(from).add(edge);
            inDegree.merge(to, 1, Integer::sum);
        }

        if (strict) {
            validateConditionEdges(byKey, outEdges, errors);

            if (hasCycle(inDegree, outEdges)) {
                add(errors, "工作流存在循环依赖，请检查节点之间的指向");
            }

            List<String> roots = inDegree.entrySet().stream()
                    .filter(e -> e.getValue() == 0)
                    .map(Map.Entry::getKey)
                    .toList();
            if (roots.isEmpty() && !byKey.isEmpty()) {
                add(errors, "工作流缺少起始节点（每个节点都有前置依赖）");
            }

            // 多起点是合法的（并行分支），但既无入边又无出边的节点一定是编辑残留。
            // 注意不能用「能否从起点到达」来判断：孤立节点自己就是起点，永远可达。
            if (byKey.size() > 1) {
                for (String key : byKey.keySet()) {
                    if (inDegree.getOrDefault(key, 0) == 0 && outEdges.getOrDefault(key, List.of()).isEmpty()) {
                        add(errors, "节点 " + key + " 没有任何依赖关系，请连接它或删除");
                    }
                }
            }

            if (!roots.isEmpty()) {
                Set<String> reachable = reachableFrom(roots, outEdges);
                List<String> unreachable = byKey.keySet().stream()
                        .filter(k -> !reachable.contains(k))
                        .toList();
                if (!unreachable.isEmpty()) {
                    add(errors, "以下节点无法从起始节点到达：" + String.join(", ", unreachable));
                }
            }
        }

        return errors.isEmpty() ? ValidationResult.ok() : ValidationResult.fail(errors);
    }

    private void validateConditionEdges(Map<String, NodeSpec> byKey,
                                        Map<String, List<EdgeSpec>> outEdges,
                                        List<String> errors) {
        for (NodeSpec node : byKey.values()) {
            NodeType type = NodeType.from(node.nodeType());
            if (type == null) {
                continue;
            }
            List<EdgeSpec> outs = outEdges.getOrDefault(node.nodeKey(), List.of());
            if (type == NodeType.CONDITION) {
                Set<String> flags = new LinkedHashSet<>();
                for (EdgeSpec edge : outs) {
                    String value = edge.conditionValue();
                    if (value == null || value.isBlank()) {
                        add(errors, "条件节点 " + node.nodeKey() + " 的出边必须标记 TRUE 或 FALSE");
                    } else if (!"TRUE".equalsIgnoreCase(value) && !"FALSE".equalsIgnoreCase(value)) {
                        add(errors, "条件节点 " + node.nodeKey() + " 的出边标记只能是 TRUE 或 FALSE：" + value);
                    } else {
                        flags.add(value.toUpperCase());
                    }
                }
                if (outs.size() != 2 || !flags.equals(Set.of("TRUE", "FALSE"))) {
                    add(errors, "条件节点 " + node.nodeKey() + " 必须恰好两条出边，且分别标记 TRUE 与 FALSE");
                }
            } else {
                boolean hasFlag = outs.stream()
                        .anyMatch(edge -> edge.conditionValue() != null && !edge.conditionValue().isBlank());
                if (hasFlag) {
                    add(errors, "非条件节点 " + node.nodeKey() + " 的出边不能带 TRUE/FALSE 标记");
                }
            }
        }
    }

    /** Kahn 拓扑排序：能全部出队说明无环。 */
    private boolean hasCycle(Map<String, Integer> inDegree, Map<String, List<EdgeSpec>> outEdges) {
        Map<String, Integer> degree = new HashMap<>(inDegree);
        Deque<String> queue = new ArrayDeque<>();
        degree.forEach((key, value) -> {
            if (value == 0) {
                queue.add(key);
            }
        });

        int visited = 0;
        while (!queue.isEmpty()) {
            String current = queue.poll();
            visited++;
            for (EdgeSpec edge : outEdges.getOrDefault(current, List.of())) {
                if (degree.merge(edge.toNodeKey(), -1, Integer::sum) == 0) {
                    queue.add(edge.toNodeKey());
                }
            }
        }
        return visited < degree.size();
    }

    private Set<String> reachableFrom(List<String> roots, Map<String, List<EdgeSpec>> outEdges) {
        Set<String> visited = new HashSet<>(roots);
        Deque<String> queue = new ArrayDeque<>(roots);
        while (!queue.isEmpty()) {
            String current = queue.poll();
            for (EdgeSpec edge : outEdges.getOrDefault(current, List.of())) {
                if (visited.add(edge.toNodeKey())) {
                    queue.add(edge.toNodeKey());
                }
            }
        }
        return visited;
    }

    private void validateConfig(String key, NodeType type, Map<String, Object> config, List<String> errors) {
        Map<String, Object> cfg = config == null ? Map.of() : config;
        switch (type) {
            case HTTP -> {
                Object url = cfg.get("url");
                if (!(url instanceof String text) || text.isBlank()) {
                    add(errors, "HTTP 节点 " + key + " 缺少 url");
                } else if (!isHttpUrl(text)) {
                    add(errors, "HTTP 节点 " + key + " 的 url 只支持 http/https：" + text);
                }
                Object method = cfg.get("method");
                if (method != null && !HTTP_METHODS.contains(String.valueOf(method).toUpperCase())) {
                    add(errors, "HTTP 节点 " + key + " 的 method 不支持：" + method);
                }
                Object headers = cfg.get("headers");
                if (headers != null && !(headers instanceof Map)) {
                    add(errors, "HTTP 节点 " + key + " 的 headers 必须是对象");
                }
            }
            case CONDITION -> {
                Object field = cfg.get("field");
                if (!(field instanceof String text) || text.isBlank()) {
                    add(errors, "条件节点 " + key + " 缺少 field");
                }
                Object operator = cfg.get("operator");
                if (!(operator instanceof String text) || text.isBlank()) {
                    add(errors, "条件节点 " + key + " 缺少 operator");
                } else if (!CONDITION_OPERATORS.contains(text.toUpperCase())) {
                    add(errors, "条件节点 " + key + " 的 operator 不支持：" + text);
                }
            }
            case MOCK_TASK -> {
                Object duration = cfg.get("durationMs");
                if (duration instanceof Number number
                        && (number.longValue() < 0 || number.longValue() > 60_000)) {
                    add(errors, "模拟节点 " + key + " 的 durationMs 需在 0-60000 之间");
                }
                Object failRate = cfg.get("failRate");
                if (failRate instanceof Number number
                        && (number.doubleValue() < 0 || number.doubleValue() > 1)) {
                    add(errors, "模拟节点 " + key + " 的 failRate 需在 0-1 之间");
                }
            }
            case NOTIFY -> {
                Object title = cfg.get("title");
                if (!(title instanceof String text) || text.isBlank()) {
                    add(errors, "通知节点 " + key + " 缺少 title");
                }
                Object content = cfg.get("content");
                if (content instanceof String text && text.length() > 1024) {
                    add(errors, "通知节点 " + key + " 的 content 不能超过 1024 字符");
                }
            }
        }
    }

    private boolean isHttpUrl(String value) {
        try {
            URI uri = URI.create(value);
            String scheme = uri.getScheme();
            return ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                    && uri.getHost() != null;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    /** 错误过多时截断：用户一次能消化的量有限，堆一百条反而看不清重点。 */
    private void add(List<String> errors, String message) {
        if (errors.size() < MAX_ERRORS) {
            errors.add(message);
        }
    }
}
