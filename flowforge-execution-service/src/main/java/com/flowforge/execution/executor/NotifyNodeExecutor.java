package com.flowforge.execution.executor;

import com.flowforge.common.contract.NodeType;
import com.flowforge.execution.engine.NodeContext;
import com.flowforge.execution.engine.NodeExecutor;
import com.flowforge.execution.engine.NodeResult;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 通知节点。
 *
 * <p>阶段三只把通知内容作为节点产出记录下来；真正写入站内通知由通知服务消费
 * {@code workflow.finished} 事件完成（阶段四接入消息后）。这样可以避免
 * 「执行服务直接写别人的库」这种边界破坏。
 */
@Component
public class NotifyNodeExecutor implements NodeExecutor {

    @Override
    public NodeType type() {
        return NodeType.NOTIFY;
    }

    @Override
    public NodeResult execute(NodeContext context) {
        Map<String, Object> config = context.config();
        String channel = String.valueOf(config.getOrDefault("channel", "INBOX"));
        String title = String.valueOf(config.getOrDefault("title", ""));
        String content = String.valueOf(config.getOrDefault("content", ""));

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("channel", channel);
        output.put("title", title);
        output.put("content", content);

        return NodeResult.success(output, "notify " + channel + " " + title, "queued");
    }
}
