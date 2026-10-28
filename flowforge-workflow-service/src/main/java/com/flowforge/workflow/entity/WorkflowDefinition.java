package com.flowforge.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@TableName("workflow_definition")
@Getter
@Setter
public class WorkflowDefinition {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long ownerId;

    private String name;

    private String description;

    /** DRAFT / PUBLISHED / DISABLED */
    private String status;

    private Long currentVersionId;

    private Integer latestVersionNo;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
