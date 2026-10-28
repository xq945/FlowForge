-- 调度服务表结构（对应 docs/04-数据库设计.md 第 4 节）

CREATE DATABASE IF NOT EXISTS flowforge_scheduler
    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

GRANT ALL PRIVILEGES ON flowforge_scheduler.* TO 'flowforge'@'%';
FLUSH PRIVILEGES;

USE flowforge_scheduler;

CREATE TABLE IF NOT EXISTS schedule_job (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    workflow_id    BIGINT UNSIGNED NOT NULL,
    workflow_version_id BIGINT UNSIGNED DEFAULT NULL COMMENT '留空表示始终使用最新的发布版本',
    owner_id       BIGINT UNSIGNED NOT NULL,
    name           VARCHAR(64)  NOT NULL,
    cron_expr      VARCHAR(64)  NOT NULL COMMENT '6 位标准 cron',
    timezone       VARCHAR(64)  NOT NULL DEFAULT 'Asia/Shanghai',
    enabled        TINYINT      NOT NULL DEFAULT 1,
    next_fire_time DATETIME(3)  NOT NULL COMMENT '下次应当触发的时间',
    last_fire_time DATETIME(3)  DEFAULT NULL,
    created_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    version        INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_workflow_cron (workflow_id, cron_expr),
    KEY idx_due (enabled, next_fire_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS schedule_execution (
    id                    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    job_id                BIGINT UNSIGNED NOT NULL,
    fire_time             DATETIME(3) NOT NULL COMMENT '本次触发对应的计划时间',
    status                VARCHAR(16) NOT NULL COMMENT 'CREATED / FAILED',
    workflow_execution_id BIGINT UNSIGNED DEFAULT NULL COMMENT '创建出的执行实例',
    error_message         VARCHAR(512) DEFAULT NULL,
    created_at            DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at            DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_job_fire (job_id, fire_time),
    KEY idx_status_created (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
