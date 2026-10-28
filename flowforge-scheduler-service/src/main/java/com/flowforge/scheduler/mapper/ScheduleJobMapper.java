package com.flowforge.scheduler.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.flowforge.scheduler.entity.ScheduleJob;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface ScheduleJobMapper extends BaseMapper<ScheduleJob> {

    /**
     * 到期任务扫描。
     *
     * <p>不做「领取 + 租约」：真正的防重复靠 {@code schedule_execution.uk_job_fire}
     * 唯一键，多实例同时扫到同一条任务时，只有一个能插入成功。
     */
    @Select("""
            SELECT * FROM schedule_job
             WHERE enabled = 1
               AND next_fire_time <= NOW(3)
             ORDER BY next_fire_time
             LIMIT #{limit}
            """)
    List<ScheduleJob> selectDue(@Param("limit") int limit);
}
