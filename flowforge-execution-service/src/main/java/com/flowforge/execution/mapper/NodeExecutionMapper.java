package com.flowforge.execution.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.flowforge.execution.entity.NodeExecution;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

public interface NodeExecutionMapper extends BaseMapper<NodeExecution> {

    /**
     * 状态迁移的唯一入口：带前置状态条件，天然原子。
     *
     * <p>返回 0 表示「状态已被别人改过」——并发消费、重复消息、重复推进都靠这一条吸收，
     * 调用方不应把它当成错误。
     */
    @Update("""
            UPDATE node_execution
               SET status = #{target},
                   started_at = CASE WHEN #{target} = 'RUNNING' THEN NOW(3) ELSE started_at END,
                   finished_at = CASE WHEN #{target} IN ('SUCCESS','FAILED','SKIPPED','CANCELLED')
                                      THEN NOW(3) ELSE finished_at END,
                   output_json = COALESCE(#{output}, output_json),
                   error_code = #{errorCode},
                   error_message = #{errorMessage},
                   updated_at = NOW(3)
             WHERE id = #{id}
               AND status = #{expected}
            """)
    int transition(@Param("id") Long id,
                   @Param("expected") String expected,
                   @Param("target") String target,
                   @Param("output") String output,
                   @Param("errorCode") String errorCode,
                   @Param("errorMessage") String errorMessage);

    /** 抢占执行：影响行数为 0 说明已被其他线程/实例抢走。 */
    @Update("""
            UPDATE node_execution
               SET status = 'RUNNING',
                   attempt_no = attempt_no + 1,
                   started_at = NOW(3),
                   updated_at = NOW(3)
             WHERE id = #{id}
               AND status = 'READY'
            """)
    int claimForRun(@Param("id") Long id);

    /** 进入退避等待：重试次数 +1，并记录下次可重试时间。 */
    @Update("""
            UPDATE node_execution
               SET retry_count = retry_count + 1,
                   next_retry_at = #{nextRetryAt},
                   updated_at = NOW(3)
             WHERE id = #{id}
               AND status = 'RETRY_WAIT'
            """)
    int scheduleRetry(@Param("id") Long id, @Param("nextRetryAt") LocalDateTime nextRetryAt);

    /** 退避到期，重新回到待执行队列。 */
    @Update("""
            UPDATE node_execution
               SET status = 'READY',
                   next_retry_at = NULL,
                   updated_at = NOW(3)
             WHERE id = #{id}
               AND status = 'RETRY_WAIT'
            """)
    int releaseForRetry(@Param("id") Long id);
}
