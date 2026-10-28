package com.flowforge.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.flowforge.workflow.entity.WorkflowDefinition;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface WorkflowDefinitionMapper extends BaseMapper<WorkflowDefinition> {

    /**
     * 发布时锁住定义行，把同一工作流的并发发布串行化。
     *
     * <p>不用 Redis 锁：锁与事务边界对不齐，而这里需要的正是「事务提交前一直持有」。
     */
    @Select("SELECT * FROM workflow_definition WHERE id = #{id} FOR UPDATE")
    WorkflowDefinition selectForUpdate(@Param("id") Long id);
}
