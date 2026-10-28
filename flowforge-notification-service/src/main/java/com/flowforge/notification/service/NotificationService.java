package com.flowforge.notification.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.flowforge.common.api.BizException;
import com.flowforge.common.api.ErrorCode;
import com.flowforge.common.api.PageResult;
import com.flowforge.common.util.SensitiveDataMasker;
import com.flowforge.notification.dto.NotificationView;
import com.flowforge.notification.entity.NotificationDelivery;
import com.flowforge.notification.entity.NotificationRecord;
import com.flowforge.notification.mapper.NotificationDeliveryMapper;
import com.flowforge.notification.mapper.NotificationRecordMapper;
import com.flowforge.notification.messaging.WorkflowFinishedMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";
    private static final String STATUS_TIMED_OUT = "TIMED_OUT";
    private static final String STATUS_CANCELLED = "CANCELLED";

    private final NotificationRecordMapper recordMapper;
    private final NotificationDeliveryMapper deliveryMapper;

    public NotificationService(NotificationRecordMapper recordMapper,
                               NotificationDeliveryMapper deliveryMapper) {
        this.recordMapper = recordMapper;
        this.deliveryMapper = deliveryMapper;
    }

    /**
     * 消费工作流终结事件并生成站内通知。
     *
     * <p>幂等由 {@code uk_source_event} 保证：同一条消息重复投递时，
     * 第二笔插入直接冲突，静默返回。
     */
    @Transactional
    public void handleWorkflowFinished(WorkflowFinishedMessage event) {
        if (event.executionId() == null || event.ownerId() == null) {
            log.warn("工作流终结消息缺少必要字段，跳过 eventId={}", event.eventId());
            return;
        }

        NotificationRecord record = new NotificationRecord();
        record.setUserId(event.ownerId());
        record.setType(typeOf(event.status()));
        record.setTitle(titleOf(event.status()));
        record.setContent(contentOf(event));
        record.setBizType("EXECUTION");
        record.setBizId(event.executionId());
        record.setSourceEventId(event.eventId());
        record.setReadFlag(0);

        try {
            recordMapper.insert(record);
        } catch (DuplicateKeyException ex) {
            // 重复消息：通知已经生成过，直接返回
            log.debug("通知已存在，忽略重复消息 eventId={}", event.eventId());
            return;
        }

        NotificationDelivery delivery = new NotificationDelivery();
        delivery.setNotificationId(record.getId());
        delivery.setChannel("INBOX");
        delivery.setStatus("SENT");
        delivery.setAttempts(1);
        deliveryMapper.insert(delivery);

        log.info("生成站内通知 notificationId={} executionId={} status={}",
                record.getId(), event.executionId(), event.status());
    }

    public PageResult<NotificationView> list(Long userId, boolean unreadOnly, long page, long size) {
        Page<NotificationRecord> query = new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), 100));
        LambdaQueryWrapper<NotificationRecord> wrapper = new LambdaQueryWrapper<NotificationRecord>()
                .eq(NotificationRecord::getUserId, userId)
                .eq(unreadOnly, NotificationRecord::getReadFlag, 0)
                .orderByDesc(NotificationRecord::getId);
        Page<NotificationRecord> result = recordMapper.selectPage(query, wrapper);

        List<NotificationView> records = result.getRecords().stream()
                .map(item -> new NotificationView(
                        item.getId(),
                        item.getType(),
                        item.getTitle(),
                        item.getContent(),
                        item.getBizType(),
                        item.getBizId(),
                        item.getReadFlag() != null && item.getReadFlag() == 1,
                        item.getCreatedAt()))
                .toList();
        return PageResult.of(records, result.getTotal(), query.getCurrent(), query.getSize());
    }

    public long unreadCount(Long userId) {
        return recordMapper.selectCount(new LambdaQueryWrapper<NotificationRecord>()
                .eq(NotificationRecord::getUserId, userId)
                .eq(NotificationRecord::getReadFlag, 0));
    }

    public void markRead(Long userId, Long notificationId) {
        NotificationRecord record = recordMapper.selectById(notificationId);
        // 不区分「不存在」与「不是你的」，避免通过错误码探测他人通知
        if (record == null || !record.getUserId().equals(userId)) {
            throw new BizException(ErrorCode.NOT_FOUND, "通知不存在");
        }
        recordMapper.markRead(notificationId, userId, LocalDateTime.now());
    }

    private String typeOf(String status) {
        if (STATUS_SUCCESS.equals(status)) {
            return "WORKFLOW_SUCCESS";
        }
        if (STATUS_TIMED_OUT.equals(status)) {
            return "WORKFLOW_TIMED_OUT";
        }
        if (STATUS_CANCELLED.equals(status)) {
            return "WORKFLOW_CANCELLED";
        }
        return "WORKFLOW_FAILED";
    }

    private String titleOf(String status) {
        if (STATUS_SUCCESS.equals(status)) {
            return "工作流执行成功";
        }
        if (STATUS_TIMED_OUT.equals(status)) {
            return "工作流执行超时";
        }
        if (STATUS_CANCELLED.equals(status)) {
            return "工作流已取消";
        }
        return "工作流执行失败";
    }

    private String contentOf(WorkflowFinishedMessage event) {
        String base = "执行实例 #" + event.executionId() + " 已结束，状态：" + event.status();
        if (event.errorMessage() == null || event.errorMessage().isBlank()) {
            return base;
        }
        String reason = SensitiveDataMasker.truncate(event.errorMessage(), 400);
        return base + "；原因：" + reason;
    }
}
