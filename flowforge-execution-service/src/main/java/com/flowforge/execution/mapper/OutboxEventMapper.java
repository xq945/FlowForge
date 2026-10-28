package com.flowforge.execution.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.flowforge.execution.entity.OutboxEvent;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

public interface OutboxEventMapper extends BaseMapper<OutboxEvent> {

    /**
     * 领取待发布事件。
     *
     * <p>不用事务 + 行锁：发布是一次网络 IO，长时间持锁会把库连接拖住。
     * 多实例同时捞到同一条只会导致重复投递，而消费端本来就按「消息可能重复」设计。
     */
    @Select("""
            SELECT * FROM outbox_event
             WHERE status = 'PENDING'
               AND (next_retry_at IS NULL OR next_retry_at <= NOW(3))
             ORDER BY id
             LIMIT #{limit}
            """)
    List<OutboxEvent> selectPending(@Param("limit") int limit);

    @Update("""
            UPDATE outbox_event
               SET status = 'SENT', sent_at = NOW(3), updated_at = NOW(3)
             WHERE id = #{id}
               AND status = 'PENDING'
            """)
    int markSent(@Param("id") Long id);

    @Update("""
            UPDATE outbox_event
               SET status = 'FAILED', last_error = #{error}, updated_at = NOW(3)
             WHERE id = #{id}
            """)
    int markFailed(@Param("id") Long id, @Param("error") String error);

    @Update("""
            UPDATE outbox_event
               SET retry_count = retry_count + 1,
                   next_retry_at = #{nextRetryAt},
                   last_error = #{error}
             WHERE id = #{id}
            """)
    int markRetry(@Param("id") Long id,
                  @Param("nextRetryAt") LocalDateTime nextRetryAt,
                  @Param("error") String error);

    @Select("""
            SELECT COUNT(*) FROM outbox_event
             WHERE status = 'PENDING'
               AND created_at < #{before}
            """)
    long countStuck(@Param("before") LocalDateTime before);
}
