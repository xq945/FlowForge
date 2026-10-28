-- 通知服务表结构（对应 docs/04-数据库设计.md 第 6 节）

CREATE DATABASE IF NOT EXISTS flowforge_notification
    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

GRANT ALL PRIVILEGES ON flowforge_notification.* TO 'flowforge'@'%';
FLUSH PRIVILEGES;

USE flowforge_notification;

CREATE TABLE IF NOT EXISTS notification_record (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id         BIGINT UNSIGNED NOT NULL,
    type            VARCHAR(32) NOT NULL COMMENT 'WORKFLOW_SUCCESS/WORKFLOW_FAILED/WORKFLOW_TIMED_OUT',
    title           VARCHAR(128) NOT NULL,
    content         VARCHAR(1024) NOT NULL,
    biz_type        VARCHAR(32) NOT NULL COMMENT 'EXECUTION',
    biz_id          BIGINT UNSIGNED NOT NULL COMMENT '执行实例 ID',
    source_event_id CHAR(36) NOT NULL COMMENT '来源消息 eventId，幂等键',
    read_flag       TINYINT NOT NULL DEFAULT 0,
    read_at         DATETIME(3) DEFAULT NULL,
    created_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_source_event (source_event_id),
    KEY idx_user_read (user_id, read_flag, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS notification_delivery (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    notification_id BIGINT UNSIGNED NOT NULL,
    channel         VARCHAR(16) NOT NULL COMMENT 'INBOX / MOCK_EMAIL',
    status          VARCHAR(16) NOT NULL COMMENT 'PENDING/SENT/FAILED',
    attempts        INT NOT NULL DEFAULT 0,
    next_retry_at   DATETIME(3) DEFAULT NULL,
    last_error      VARCHAR(512) DEFAULT NULL,
    created_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_notification_channel (notification_id, channel),
    KEY idx_status_retry (status, next_retry_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
