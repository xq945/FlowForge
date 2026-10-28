-- 工作流服务表结构（对应 docs/04-数据库设计.md 第 3 节）
-- 自包含：可用于全新初始化，也可在已有实例上单独执行

CREATE DATABASE IF NOT EXISTS flowforge_workflow
    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

GRANT ALL PRIVILEGES ON flowforge_workflow.* TO 'flowforge'@'%';
FLUSH PRIVILEGES;

USE flowforge_workflow;

CREATE TABLE IF NOT EXISTS workflow_definition (
    id                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    owner_id           BIGINT UNSIGNED NOT NULL COMMENT '所属用户',
    name               VARCHAR(64)  NOT NULL,
    description        VARCHAR(512) DEFAULT NULL,
    status             VARCHAR(16)  NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PUBLISHED/DISABLED',
    current_version_id BIGINT UNSIGNED DEFAULT NULL COMMENT '当前生效的发布版本',
    latest_version_no  INT          NOT NULL DEFAULT 0 COMMENT '已发布的最大版本号，0=从未发布',
    created_at         DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at         DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_owner_name (owner_id, name),
    KEY idx_owner_status (owner_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS workflow_version (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    workflow_id  BIGINT UNSIGNED NOT NULL,
    version_no   INT          NOT NULL COMMENT '0=当前草稿；≥1 为发布版本',
    status       VARCHAR(16)  NOT NULL COMMENT 'DRAFT / PUBLISHED',
    content_hash CHAR(64)     DEFAULT NULL COMMENT '发布内容 SHA-256',
    published_at DATETIME(3)  DEFAULT NULL,
    published_by BIGINT UNSIGNED DEFAULT NULL,
    created_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_workflow_version (workflow_id, version_no),
    KEY idx_workflow_status (workflow_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS workflow_node (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    workflow_version_id BIGINT UNSIGNED NOT NULL,
    node_key            VARCHAR(64)  NOT NULL COMMENT '版本内稳定业务键',
    node_type           VARCHAR(32)  NOT NULL COMMENT 'HTTP / CONDITION / MOCK_TASK / NOTIFY',
    name                VARCHAR(128) NOT NULL,
    config              JSON         NOT NULL COMMENT '经校验的类型化配置',
    timeout_ms          INT          NOT NULL DEFAULT 30000,
    retry_max           INT          NOT NULL DEFAULT 0,
    retry_initial_ms    INT          NOT NULL DEFAULT 1000,
    retry_max_ms        INT          NOT NULL DEFAULT 60000,
    retry_on_uncertain  TINYINT      NOT NULL DEFAULT 0 COMMENT '读超时是否自动重试',
    pos_x               INT          NOT NULL DEFAULT 0,
    pos_y               INT          NOT NULL DEFAULT 0,
    created_at          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_version_node (workflow_version_id, node_key),
    KEY idx_version (workflow_version_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS workflow_edge (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    workflow_version_id BIGINT UNSIGNED NOT NULL,
    from_node_key       VARCHAR(64) NOT NULL,
    to_node_key         VARCHAR(64) NOT NULL,
    condition_value     VARCHAR(8) DEFAULT NULL COMMENT 'TRUE/FALSE，仅条件节点出边非空',
    created_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_version_edge (workflow_version_id, from_node_key, to_node_key),
    KEY idx_version (workflow_version_id),
    KEY idx_to (workflow_version_id, to_node_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
