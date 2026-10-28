package com.flowforge.execution.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@TableName("workflow_execution")
@Getter
@Setter
public class WorkflowExecution {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long workflowId;

    /** 启动时绑定的版本，后续编辑与重新发布都不影响它 */
    private Long workflowVersionId;

    private Integer versionNo;

    private Long ownerId;

    /** MANUAL / SCHEDULE / MANUAL_RETRY */
    private String triggerType;

    /** 幂等键：手动为 requestId，周期为 jobId:fireTime */
    private String triggerKey;

    private String status;

    private Integer cancelRequested;

    private Integer totalNodes;

    private Integer finishedNodes;

    /** 版本快照中的依赖边（JSON），执行引擎据此重算可达性 */
    private String edgesSnapshot;

    private LocalDateTime deadlineAt;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    private String errorCode;

    private String errorMessage;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Integer version;
}
