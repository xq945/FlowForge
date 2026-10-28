package com.flowforge.execution.executor;

import com.flowforge.common.contract.NodeType;
import com.flowforge.execution.engine.NodeContext;
import com.flowforge.execution.engine.NodeExecutor;
import com.flowforge.execution.engine.NodeResult;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 模拟业务处理节点。
 *
 * <p>存在的意义是让重试、退避、失败终止传播这些机制**可被稳定复现**：
 * 配置 {@code failRate} 就能在演示与测试里制造失败，而不必等真实依赖出问题。
 */
@Component
public class MockTaskNodeExecutor implements NodeExecutor {

    @Override
    public NodeType type() {
        return NodeType.MOCK_TASK;
    }

    @Override
    public NodeResult execute(NodeContext context) {
        Map<String, Object> config = context.config();
        long durationMs = Math.max(0, longOf(config.get("durationMs"), 0));
        double failRate = Math.min(1.0, Math.max(0.0, doubleOf(config.get("failRate"), 0.0)));

        try {
            if (durationMs > 0) {
                Thread.sleep(durationMs);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return NodeResult.failure("MOCK_INTERRUPTED", "模拟节点被中断",
                    false, false, "mock sleep " + durationMs + "ms", null);
        }

        String summary = "mock duration=" + durationMs + "ms failRate=" + failRate;

        if (failRate > 0 && ThreadLocalRandom.current().nextDouble() < failRate) {
            // 模拟的失败属于「可重试」：演示重试策略时正是需要它
            return NodeResult.failure("MOCK_FAILED", "模拟失败（failRate=" + failRate + "）",
                    true, false, summary, null);
        }

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("mock", "ok");
        output.put("durationMs", durationMs);
        return NodeResult.success(output, summary, "success");
    }

    private long longOf(Object value, long fallback) {
        return value instanceof Number number ? number.longValue() : fallback;
    }

    private double doubleOf(Object value, double fallback) {
        return value instanceof Number number ? number.doubleValue() : fallback;
    }
}
