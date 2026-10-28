package com.flowforge.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@TableName("workflow_edge")
@Getter
@Setter
public class WorkflowEdge {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long workflowVersionId;

    private String fromNodeKey;

    private String toNodeKey;

    /** TRUE / FALSE，仅条件节点出边非空 */
    private String conditionValue;

    private LocalDateTime createdAt;
}
