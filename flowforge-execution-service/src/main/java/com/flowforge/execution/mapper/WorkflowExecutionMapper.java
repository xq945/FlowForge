package com.flowforge.execution.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.flowforge.execution.entity.WorkflowExecution;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface WorkflowExecutionMapper extends BaseMapper<WorkflowExecution> {

    /**
     * 推进节点前锁住实例行，把「重算可达性 + 推进后继 + 判定终结」串行化。
     *
     * <p>多个节点同时完成时，正是靠这把行锁避免实例状态互相覆盖。
     */
    @Select("SELECT * FROM workflow_execution WHERE id = #{id} FOR UPDATE")
    WorkflowExecution selectForUpdate(@Param("id") Long id);

    /**
     * 用重算结果覆盖完成计数。
     *
     * <p>相比「每完成一个 +1」，重算的优点是幂等：即使同一节点被重复推进、
     * 或某次推进因异常回滚，下一轮 advance 会把计数修正回正确值。
     * 代价是每次多一次 COUNT 查询——节点数在百级以内，可以接受。
     */
    @Update("""
            UPDATE workflow_execution
               SET finished_nodes = #{count},
                   updated_at = NOW(3)
             WHERE id = #{id}
               AND status = 'RUNNING'
            """)
    int updateFinishedNodes(@Param("id") Long id, @Param("count") int count);

    /**
     * 终态迁移。只有 {@code status='RUNNING'} 时才生效，
     * 因此并发完成时只有一个线程能真正把实例置为终态并发出通知。
     */
    @Update("""
            UPDATE workflow_execution
               SET status = #{target},
                   finished_at = NOW(3),
                   error_code = #{errorCode},
                   error_message = #{errorMessage},
                   updated_at = NOW(3)
             WHERE id = #{id}
               AND status = 'RUNNING'
            """)
    int finishIfRunning(@Param("id") Long id,
                        @Param("target") String target,
                        @Param("errorCode") String errorCode,
                        @Param("errorMessage") String errorMessage);

    /** 超过实例级截止时间仍在运行的实例。 */
    @Select("""
            SELECT * FROM workflow_execution
             WHERE status = 'RUNNING'
               AND deadline_at IS NOT NULL
               AND deadline_at < NOW(3)
             ORDER BY deadline_at
             LIMIT #{limit}
            """)
    java.util.List<WorkflowExecution> selectTimedOut(@Param("limit") int limit);
}
