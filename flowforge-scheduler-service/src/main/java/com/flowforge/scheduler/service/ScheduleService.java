package com.flowforge.scheduler.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.flowforge.common.api.BizException;
import com.flowforge.common.api.ErrorCode;
import com.flowforge.common.security.LoginUser;
import com.flowforge.scheduler.dto.CreateScheduleRequest;
import com.flowforge.scheduler.dto.ScheduleJobView;
import com.flowforge.scheduler.entity.ScheduleExecution;
import com.flowforge.scheduler.entity.ScheduleJob;
import com.flowforge.scheduler.mapper.ScheduleExecutionMapper;
import com.flowforge.scheduler.mapper.ScheduleJobMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 周期任务的配置与扫描。
 *
 * <p>这里**不自己跑定时器**：何时扫描由 XXL-JOB 调度中心决定，
 * 调度服务只负责「扫到期的任务 → 创建执行实例 → 推进下次触发时间」。
 *
 * <p>防重复触发有两道：
 * <ol>
 *   <li>{@code schedule_execution.uk_job_fire} 唯一键：多实例同时扫到同一条，只有一个能占位；</li>
 *   <li>{@code workflow_execution.uk_trigger}：即使唯一键被绕过，执行侧也不会创建第二个实例。</li>
 * </ol>
 */
@Service
public class ScheduleService {

    private static final Logger log = LoggerFactory.getLogger(ScheduleService.class);
    private static final String DEFAULT_TIMEZONE = "Asia/Shanghai";

    private final ScheduleJobMapper jobMapper;
    private final ScheduleExecutionMapper executionMapper;
    private final ExecutionTriggerService triggerService;

    public ScheduleService(ScheduleJobMapper jobMapper,
                           ScheduleExecutionMapper executionMapper,
                           ExecutionTriggerService triggerService) {
        this.jobMapper = jobMapper;
        this.executionMapper = executionMapper;
        this.triggerService = triggerService;
    }

    @Transactional
    public ScheduleJobView create(LoginUser user, CreateScheduleRequest request) {
        CronExpression cron = parseCron(request.cronExpr());
        LocalDateTime next = cron.next(LocalDateTime.now());
        if (next == null) {
            throw new BizException(ErrorCode.INVALID_PARAM, "cron 表达式不会再次触发：" + request.cronExpr());
        }

        ScheduleJob job = new ScheduleJob();
        job.setWorkflowId(request.workflowId());
        job.setOwnerId(user.userId());
        job.setName(request.name().trim());
        job.setCronExpr(request.cronExpr().trim());
        job.setTimezone(request.timezone() == null || request.timezone().isBlank()
                ? DEFAULT_TIMEZONE : request.timezone());
        job.setEnabled(1);
        job.setNextFireTime(next);
        job.setVersion(0);
        try {
            jobMapper.insert(job);
        } catch (DuplicateKeyException ex) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "该工作流已存在相同的 cron 配置");
        }

        log.info("周期任务创建 jobId={} workflowId={} cron={} nextFireTime={}",
                job.getId(), job.getWorkflowId(), job.getCronExpr(), next);
        return toView(job);
    }

    public List<ScheduleJobView> list(LoginUser user, Long workflowId) {
        return jobMapper.selectList(new LambdaQueryWrapper<ScheduleJob>()
                        .eq(ScheduleJob::getOwnerId, user.userId())
                        .eq(workflowId != null, ScheduleJob::getWorkflowId, workflowId)
                        .orderByDesc(ScheduleJob::getId))
                .stream()
                .map(this::toView)
                .toList();
    }

    public void setEnabled(LoginUser user, Long jobId, boolean enabled) {
        ScheduleJob job = jobMapper.selectById(jobId);
        if (job == null || !job.getOwnerId().equals(user.userId())) {
            throw new BizException(ErrorCode.NOT_FOUND, "周期任务不存在");
        }
        job.setEnabled(enabled ? 1 : 0);
        if (enabled) {
            CronExpression cron = parseCron(job.getCronExpr());
            LocalDateTime next = cron.next(LocalDateTime.now());
            job.setNextFireTime(next == null ? job.getNextFireTime() : next);
        }
        jobMapper.updateById(job);
    }

    /**
     * 扫描到期任务并触发。返回本次成功触发的数量。
     *
     * <p>由 XXL-JOB 的任务调用，而不是本服务自己的 {@code @Scheduled}——
     * 多实例部署时由调度中心决定谁在什么时候执行，避免每个实例各扫一遍。
     */
    public int scanDue(int limit) {
        List<ScheduleJob> due = jobMapper.selectDue(limit);
        int triggered = 0;
        for (ScheduleJob job : due) {
            if (fire(job)) {
                triggered++;
            }
        }
        if (triggered > 0) {
            log.info("本次扫描触发 {} 个周期任务", triggered);
        }
        return triggered;
    }

    private boolean fire(ScheduleJob job) {
        LocalDateTime fireTime = job.getNextFireTime();

        // 1. 先占位：唯一键决定「这一刻由谁负责」
        ScheduleExecution record = new ScheduleExecution();
        record.setJobId(job.getId());
        record.setFireTime(fireTime);
        record.setStatus("CREATED");
        try {
            executionMapper.insert(record);
        } catch (DuplicateKeyException ex) {
            log.debug("本次触发已被其它实例处理 jobId={} fireTime={}", job.getId(), fireTime);
            advanceNextFireTime(job);
            return false;
        }

        // 2. 触发执行；无论成败都要推进下次时间，否则会卡在同一个时间点反复重试
        try {
            Long executionId = triggerService.triggerBySchedule(
                    job.getWorkflowId(), job.getWorkflowVersionId(), job.getId(), fireTime.toString());
            record.setWorkflowExecutionId(executionId);
            executionMapper.updateById(record);
            return true;
        } catch (Exception ex) {
            record.setStatus("FAILED");
            record.setErrorMessage(truncate(ex.getMessage()));
            executionMapper.updateById(record);
            log.error("周期任务触发失败 jobId={} fireTime={}", job.getId(), fireTime, ex);
            return false;
        } finally {
            advanceNextFireTime(job);
        }
    }

    /**
     * 推进下次触发时间。
     *
     * <p>如果服务停机错过了多个周期，直接跳到**当前时间之后**的下一次，
     * 不做补偿执行——补偿会造成停机越久恢复时越猛烈的瞬时洪峰。
     */
    private void advanceNextFireTime(ScheduleJob job) {
        CronExpression cron = parseCron(job.getCronExpr());
        LocalDateTime cursor = job.getNextFireTime();
        LocalDateTime now = LocalDateTime.now();

        LocalDateTime next = cron.next(cursor);
        while (next != null && next.isBefore(now)) {
            next = cron.next(next);
        }

        job.setLastFireTime(cursor);
        job.setNextFireTime(next == null ? cursor : next);
        jobMapper.updateById(job);
    }

    private CronExpression parseCron(String expression) {
        try {
            return CronExpression.parse(expression.trim());
        } catch (IllegalArgumentException ex) {
            throw new BizException(ErrorCode.INVALID_PARAM, "cron 表达式不合法：" + expression);
        }
    }

    private ScheduleJobView toView(ScheduleJob job) {
        return new ScheduleJobView(
                job.getId(),
                job.getWorkflowId(),
                job.getName(),
                job.getCronExpr(),
                job.getTimezone(),
                job.getEnabled() != null && job.getEnabled() == 1,
                job.getNextFireTime(),
                job.getLastFireTime(),
                job.getCreatedAt());
    }

    private String truncate(String message) {
        if (message == null) {
            return "unknown";
        }
        return message.length() <= 500 ? message : message.substring(0, 500);
    }
}
