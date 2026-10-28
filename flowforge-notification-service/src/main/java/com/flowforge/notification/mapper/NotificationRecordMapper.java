package com.flowforge.notification.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.flowforge.notification.entity.NotificationRecord;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

public interface NotificationRecordMapper extends BaseMapper<NotificationRecord> {

    /** 标记已读；已读的不重复更新，保证幂等。 */
    @Update("""
            UPDATE notification_record
               SET read_flag = 1, read_at = #{readAt}
             WHERE id = #{id}
               AND user_id = #{userId}
               AND read_flag = 0
            """)
    int markRead(@Param("id") Long id,
                 @Param("userId") Long userId,
                 @Param("readAt") LocalDateTime readAt);
}
