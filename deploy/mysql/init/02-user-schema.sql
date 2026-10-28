-- 用户服务表结构（对应 docs/04-数据库设计.md 第 2 节）

USE flowforge_user;

CREATE TABLE IF NOT EXISTS sys_user (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    username        VARCHAR(64)  NOT NULL COMMENT '登录名，全局唯一',
    password_hash   VARCHAR(100) NOT NULL COMMENT 'BCrypt 自适应哈希',
    display_name    VARCHAR(64)  NOT NULL,
    email           VARCHAR(128) DEFAULT NULL,
    status          TINYINT      NOT NULL DEFAULT 1 COMMENT '1=正常 0=禁用',
    failed_attempts INT          NOT NULL DEFAULT 0 COMMENT '连续登录失败次数',
    locked_until    DATETIME(3)  DEFAULT NULL COMMENT '锁定截止时间',
    last_login_at   DATETIME(3)  DEFAULT NULL,
    created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username),
    UNIQUE KEY uk_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS sys_role (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    role_code  VARCHAR(32) NOT NULL COMMENT 'ADMIN / USER',
    role_name  VARCHAR(64) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_code (role_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS sys_user_role (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id    BIGINT UNSIGNED NOT NULL,
    role_id    BIGINT UNSIGNED NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_role (user_id, role_id),
    KEY idx_role (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO sys_role (role_code, role_name)
VALUES ('ADMIN', '管理员'),
       ('USER', '普通用户')
ON DUPLICATE KEY UPDATE role_name = VALUES(role_name);
