package com.flowforge.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@TableName("workflow_version")
@Getter
@Setter
public class WorkflowVersion {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long workflowId;

    /** 0 = 草稿；≥1 = 已发布版本 */
    private Integer versionNo;

    /** DRAFT / PUBLISHED */
    private String status;

    /** 发布内容的 SHA-256，用于提示「内容未变更」 */
    private String contentHash;

    private LocalDateTime publishedAt;

    private Long publishedBy;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
