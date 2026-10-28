package com.flowforge.execution.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@TableName("node_execution")
@Getter
@Setter
public class NodeExecution {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long executionId;

    private String nodeKey;

    private String nodeType;

    private String nodeName;

    private String status;

    private Integer retryCount;

    private Integer maxRetry;

    private Integer retryInitialMs;

    private Integer retryMaxMs;

    private Integer timeoutMs;

    private Integer attemptNo;

    private LocalDateTime nextRetryAt;

    /**
     * 节点配置快照。
     *
     * <p>执行记录必须自包含：工作流后续被编辑甚至删除，历史执行仍能解释当时发生了什么。
     */
    private String configSnapshot;

    private String outputJson;

    private String errorCode;

    private String errorMessage;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Integer version;
}
