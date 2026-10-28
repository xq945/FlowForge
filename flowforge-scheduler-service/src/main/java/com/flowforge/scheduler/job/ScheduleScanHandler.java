package com.flowforge.scheduler.job;

import com.flowforge.scheduler.service.ScheduleService;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * XXL-JOB 任务处理器：扫描到期的工作流周期任务。
 *
 * <p>在调度中心里对应一个 JobHandler = {@code flowforgeScheduleScan} 的任务，
 * cron 设为每分钟一次即可——真正的业务周期由 {@code schedule_job.cron_expr} 决定，
 * 这里只是「每分钟看一眼有没有到期」的心跳。
 */
@Component
public class ScheduleScanHandler {

    private static final Logger log = LoggerFactory.getLogger(ScheduleScanHandler.class);
    private static final int BATCH_SIZE = 50;

    private final ScheduleService scheduleService;

    public ScheduleScanHandler(ScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    @XxlJob("flowforgeScheduleScan")
    public void scan() {
        String param = XxlJobHelper.getJobParam();
        log.debug("XXL-JOB 触发周期任务扫描 param={}", param);
        try {
            int triggered = scheduleService.scanDue(BATCH_SIZE);
            XxlJobHelper.log("本次触发 {} 个周期任务", triggered);
        } catch (Exception ex) {
            log.error("周期任务扫描失败", ex);
            XxlJobHelper.handleFail("扫描失败：" + ex.getMessage());
        }
    }
}
