-- 执行服务表结构（对应 docs/04-数据库设计.md 第 5 节）

CREATE DATABASE IF NOT EXISTS flowforge_execution
    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

GRANT ALL PRIVILEGES ON flowforge_execution.* TO 'flowforge'@'%';
FLUSH PRIVILEGES;

USE flowforge_execution;

CREATE TABLE IF NOT EXISTS workflow_execution (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    workflow_id         BIGINT UNSIGNED NOT NULL,
    workflow_version_id BIGINT UNSIGNED NOT NULL COMMENT '启动时绑定的版本',
    version_no          INT          NOT NULL,
    owner_id            BIGINT UNSIGNED NOT NULL COMMENT '冗余，用于按用户查询',
    trigger_type        VARCHAR(16)  NOT NULL COMMENT 'MANUAL / SCHEDULE / MANUAL_RETRY',
    trigger_key         VARCHAR(128) NOT NULL COMMENT '幂等键：requestId 或 jobId:fireTime',
    status              VARCHAR(16)  NOT NULL COMMENT 'PENDING/RUNNING/SUCCESS/FAILED/CANCELLED/TIMED_OUT',
    cancel_requested    TINYINT      NOT NULL DEFAULT 0,
    total_nodes         INT          NOT NULL DEFAULT 0,
    finished_nodes      INT          NOT NULL DEFAULT 0,
    edges_snapshot      JSON         DEFAULT NULL COMMENT '版本快照中的依赖边，执行引擎据此重算可达性',
    deadline_at         DATETIME(3)  DEFAULT NULL COMMENT '实例级超时截止',
    started_at          DATETIME(3)  DEFAULT NULL,
    finished_at         DATETIME(3)  DEFAULT NULL,
    error_code          VARCHAR(64)  DEFAULT NULL,
    error_message       VARCHAR(512) DEFAULT NULL COMMENT '脱敏后的失败摘要',
    created_at          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    version             INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_trigger (trigger_type, trigger_key),
    KEY idx_workflow_created (workflow_id, created_at),
    KEY idx_owner_created (owner_id, created_at),
    KEY idx_status_deadline (status, deadline_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS node_execution (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    execution_id    BIGINT UNSIGNED NOT NULL,
    node_key        VARCHAR(64) NOT NULL,
    node_type       VARCHAR(32) NOT NULL,
    node_name       VARCHAR(128) NOT NULL,
    status          VARCHAR(16) NOT NULL COMMENT 'WAITING/READY/RUNNING/RETRY_WAIT/SUCCESS/FAILED/SKIPPED/CANCELLED',
    retry_count     INT         NOT NULL DEFAULT 0 COMMENT '已发生的重试次数',
    max_retry       INT         NOT NULL DEFAULT 0,
    retry_initial_ms INT        NOT NULL DEFAULT 1000 COMMENT '退避基数',
    retry_max_ms    INT         NOT NULL DEFAULT 60000 COMMENT '退避上限',
    timeout_ms      INT         NOT NULL DEFAULT 30000,
    attempt_no      INT         NOT NULL DEFAULT 0 COMMENT '已发起的尝试序号',
    next_retry_at   DATETIME(3) DEFAULT NULL,
    config_snapshot JSON        DEFAULT NULL COMMENT '节点配置快照，保证执行记录自包含',
    output_json     JSON        DEFAULT NULL COMMENT '节点产出，供下游条件节点使用',
    error_code      VARCHAR(64) DEFAULT NULL,
    error_message   VARCHAR(512) DEFAULT NULL COMMENT '脱敏后',
    started_at      DATETIME(3) DEFAULT NULL,
    finished_at     DATETIME(3) DEFAULT NULL,
    created_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    version         INT         NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_execution_node (execution_id, node_key),
    KEY idx_status_retry (status, next_retry_at),
    KEY idx_execution (execution_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS execution_attempt (
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    execution_id      BIGINT UNSIGNED NOT NULL,
    node_execution_id BIGINT UNSIGNED NOT NULL,
    attempt_no        INT          NOT NULL COMMENT '从 1 开始',
    status            VARCHAR(16)  NOT NULL COMMENT 'SUCCESS / FAILED',
    error_code        VARCHAR(64)  DEFAULT NULL,
    error_message     VARCHAR(512) DEFAULT NULL COMMENT '脱敏后',
    uncertain         TINYINT      NOT NULL DEFAULT 0 COMMENT '结果未知（如读超时）',
    request_summary   VARCHAR(2048) DEFAULT NULL COMMENT '脱敏后的请求摘要',
    response_summary  VARCHAR(2048) DEFAULT NULL COMMENT '脱敏后的响应摘要',
    duration_ms       INT          DEFAULT NULL,
    started_at        DATETIME(3)  DEFAULT NULL,
    finished_at       DATETIME(3)  DEFAULT NULL,
    created_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_node_attempt (node_execution_id, attempt_no),
    KEY idx_execution (execution_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS execution_event (
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    execution_id      BIGINT UNSIGNED NOT NULL,
    node_execution_id BIGINT UNSIGNED DEFAULT NULL,
    event_type        VARCHAR(48)  NOT NULL,
    payload           JSON         DEFAULT NULL,
    created_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_execution (execution_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 阶段四使用：事务发件箱。与业务数据同库同事务写入，保证「数据落库则消息最终一定发出」
CREATE TABLE IF NOT EXISTS outbox_event (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    event_id       CHAR(36)     NOT NULL COMMENT '全局唯一事件 ID，消费端幂等依据',
    event_type     VARCHAR(64)  NOT NULL,
    aggregate_type VARCHAR(32)  NOT NULL COMMENT 'EXECUTION / NODE',
    aggregate_id   VARCHAR(64)  NOT NULL,
    payload        JSON         NOT NULL COMMENT '已脱敏，不含凭证',
    status         VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/SENT/FAILED',
    retry_count    INT          NOT NULL DEFAULT 0,
    next_retry_at  DATETIME(3)  DEFAULT NULL,
    last_error     VARCHAR(512) DEFAULT NULL,
    created_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    sent_at        DATETIME(3)  DEFAULT NULL,
    updated_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_event_id (event_id),
    KEY idx_status_next (status, next_retry_at),
    KEY idx_aggregate (aggregate_type, aggregate_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS dead_letter_record (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    event_id     CHAR(36)     NOT NULL,
    event_type   VARCHAR(64)  NOT NULL,
    source_queue VARCHAR(64)  NOT NULL,
    execution_id BIGINT UNSIGNED DEFAULT NULL,
    payload      TEXT         DEFAULT NULL COMMENT '脱敏后的原始消息，不保证是合法 JSON',
    reason       VARCHAR(512) NOT NULL,
    status       VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / RESOLVED',
    created_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    resolved_at  DATETIME(3)  DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_event_id (event_id),
    KEY idx_status_created (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
