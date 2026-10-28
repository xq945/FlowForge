package com.flowforge.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.flowforge.workflow.entity.WorkflowVersion;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface WorkflowVersionMapper extends BaseMapper<WorkflowVersion> {

    @Select("""
            SELECT * FROM workflow_version
             WHERE workflow_id = #{workflowId}
             ORDER BY version_no DESC
            """)
    List<WorkflowVersion> selectAllByWorkflow(@Param("workflowId") Long workflowId);
}
