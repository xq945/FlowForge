package com.flowforge.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@TableName("workflow_node")
@Getter
@Setter
public class WorkflowNode {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long workflowVersionId;

    /** 版本内稳定的业务键，边与执行记录都用它关联 */
    private String nodeKey;

    private String nodeType;

    private String name;

    /** 经校验的 JSON 配置 */
    private String config;

    private Integer timeoutMs;

    private Integer retryMax;

    private Integer retryInitialMs;

    private Integer retryMaxMs;

    private Integer retryOnUncertain;

    private Integer posX;

    private Integer posY;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
