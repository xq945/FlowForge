package com.flowforge.execution.executor;

import com.flowforge.common.contract.NodeType;
import com.flowforge.execution.engine.NodeContext;
import com.flowforge.execution.engine.NodeExecutor;
import com.flowforge.execution.engine.NodeResult;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 条件判断节点。
 *
 * <p>用**结构化配置**（field + operator + value）而不是表达式引擎：
 * 后者要么引入 SpEL 之类的执行风险，要么自己写一门小语言。
 * 第一版覆盖「取上游输出的某个字段与常量比较」这一足够常见的场景。
 */
@Component
public class ConditionNodeExecutor implements NodeExecutor {

    @Override
    public NodeType type() {
        return NodeType.CONDITION;
    }

    @Override
    public NodeResult execute(NodeContext context) {
        Map<String, Object> config = context.config();
        String field = String.valueOf(config.getOrDefault("field", "")).trim();
        String operator = String.valueOf(config.getOrDefault("operator", "")).trim().toUpperCase();
        Object expected = config.get("value");

        Object actual = readPath(context.upstreamOutput(), field);

        try {
            boolean matched = compare(actual, operator, expected);
            Map<String, Object> output = new LinkedHashMap<>();
            output.put("result", matched);
            output.put("field", field);
            output.put("actual", actual);
            return NodeResult.success(output,
                    "judge " + field + " " + operator + " " + expected,
                    "result=" + matched);
        } catch (IllegalArgumentException ex) {
            // 配置或数据类型错误：重试也不会变好
            return NodeResult.failure("CONDITION_EVAL_ERROR", ex.getMessage(),
                    false, false, "judge " + field, null);
        }
    }

    private boolean compare(Object actual, String operator, Object expected) {
        return switch (operator) {
            case "EQ" -> valueEquals(actual, expected);
            case "NE" -> !valueEquals(actual, expected);
            case "GT" -> toNumber(actual).compareTo(toNumber(expected)) > 0;
            case "GE" -> toNumber(actual).compareTo(toNumber(expected)) >= 0;
            case "LT" -> toNumber(actual).compareTo(toNumber(expected)) < 0;
            case "LE" -> toNumber(actual).compareTo(toNumber(expected)) <= 0;
            case "CONTAINS" -> actual != null && String.valueOf(actual).contains(String.valueOf(expected));
            case "EMPTY" -> isEmpty(actual);
            case "NOT_EMPTY" -> !isEmpty(actual);
            default -> throw new IllegalArgumentException("不支持的操作符：" + operator);
        };
    }

    private boolean valueEquals(Object actual, Object expected) {
        if (actual == null || expected == null) {
            return actual == expected;
        }
        if (actual instanceof Number || expected instanceof Number) {
            try {
                return toNumber(actual).compareTo(toNumber(expected)) == 0;
            } catch (IllegalArgumentException ex) {
                return String.valueOf(actual).equals(String.valueOf(expected));
            }
        }
        return String.valueOf(actual).equals(String.valueOf(expected));
    }

    private BigDecimal toNumber(Object value) {
        if (value == null) {
            throw new IllegalArgumentException("待比较的值为空，无法做数值比较");
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        try {
            return new BigDecimal(String.valueOf(value).trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("值不是数字，无法做数值比较：" + value);
        }
    }

    private boolean isEmpty(Object value) {
        if (value == null) {
            return true;
        }
        String text = String.valueOf(value);
        return text.isBlank() || "[]".equals(text) || "{}".equals(text);
    }

    /** 支持 {@code data.count} 这样的点号路径，逐层取 Map 里的字段。 */
    @SuppressWarnings("unchecked")
    private Object readPath(Map<String, Object> root, String path) {
        if (root == null || path == null || path.isBlank()) {
            return null;
        }
        Object current = root;
        for (String segment : path.split("\\.")) {
            if (!(current instanceof Map)) {
                return null;
            }
            current = ((Map<String, Object>) current).get(segment);
            if (current == null) {
                return null;
            }
        }
        return current;
    }
}
