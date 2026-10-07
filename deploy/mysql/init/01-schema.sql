-- 容器首次启动时自动执行（docker-entrypoint-initdb.d）
-- 两个库分别属于 auth-service 与 conn-service，服务之间不跨库直接连表。

CREATE DATABASE IF NOT EXISTS `platform_auth`
    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
CREATE DATABASE IF NOT EXISTS `platform_conn`
    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;

-- 应用账号：只有这两个库的权限，没有建库/建用户的权限
CREATE USER IF NOT EXISTS 'platform'@'%' IDENTIFIED BY 'platform123';
GRANT ALL PRIVILEGES ON `platform_auth`.* TO 'platform'@'%';
GRANT ALL PRIVILEGES ON `platform_conn`.* TO 'platform'@'%';
FLUSH PRIVILEGES;

-- ---------------------------------------------------------------------------
-- auth-service
-- ---------------------------------------------------------------------------
USE `platform_auth`;

CREATE TABLE IF NOT EXISTS `sys_user`
(
    `id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `username`   VARCHAR(64)  NOT NULL COMMENT '登录名',
    `password`   VARCHAR(100) NOT NULL COMMENT 'BCrypt 哈希',
    `nickname`   VARCHAR(64)  NOT NULL COMMENT '昵称',
    `role`       VARCHAR(32)  NOT NULL DEFAULT 'USER' COMMENT '角色',
    `status`     INT          NOT NULL DEFAULT 1 COMMENT '1=启用 0=禁用',
    `failed_attempts` INT      NOT NULL DEFAULT 0 COMMENT '连续登录失败次数',
    `locked_until`    DATETIME(3) NULL COMMENT '锁定截止时间，空=未锁定',
    `created_at` DATETIME(3)  NOT NULL,
    `updated_at` DATETIME(3)  NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_sys_user_username` (`username`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '平台用户';

-- 刷新令牌：只存 SHA-256 哈希，登出/改密即吊销，弥补无状态 JWT 无法主动失效的短板
CREATE TABLE IF NOT EXISTS `auth_refresh_token`
(
    `id`               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`          BIGINT       NOT NULL COMMENT '所属用户',
    `token_hash`       CHAR(64)     NOT NULL COMMENT 'SHA-256 十六进制哈希',
    `expires_at`       DATETIME(3)  NOT NULL COMMENT '过期时间',
    `revoked_at`       DATETIME(3)  NULL COMMENT '吊销时间，空=有效',
    `replaced_by_hash` CHAR(64)     NULL COMMENT '轮换后的新令牌哈希',
    `client_ip`        VARCHAR(64)  NULL COMMENT '签发时客户端 IP',
    `user_agent`       VARCHAR(255) NULL COMMENT '签发时 User-Agent',
    `created_at`       DATETIME(3)  NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_auth_refresh_token_hash` (`token_hash`),
    KEY `idx_auth_refresh_token_user` (`user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '刷新令牌';

-- ---------------------------------------------------------------------------
-- conn-service
-- ---------------------------------------------------------------------------
USE `platform_conn`;

CREATE TABLE IF NOT EXISTS `db_connection`
(
    `id`                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `name`              VARCHAR(128) NOT NULL COMMENT '连接名称',
    `db_type`           VARCHAR(32)  NOT NULL COMMENT 'MYSQL / POSTGRESQL',
    `host`              VARCHAR(255) NOT NULL COMMENT '主机',
    `port`              INT          NOT NULL COMMENT '端口',
    `database_name`     VARCHAR(128) NULL COMMENT '库名',
    `username`          VARCHAR(128) NOT NULL COMMENT '数据库账号',
    `password_cipher`   VARCHAR(512) NOT NULL COMMENT 'AES-GCM 密文，不存明文',
    `params`            VARCHAR(512) NULL COMMENT '附加 JDBC 参数',
    `remark`            VARCHAR(512) NULL COMMENT '备注',
    `status`            VARCHAR(32)  NOT NULL DEFAULT 'UNKNOWN' COMMENT 'UNKNOWN/OK/FAILED',
    `last_test_at`      DATETIME(3)  NULL COMMENT '最近一次测试时间',
    `last_test_message` VARCHAR(512) NULL COMMENT '最近一次测试结果',
    `created_by`        BIGINT       NULL COMMENT '创建人用户 ID',
    `created_at`        DATETIME(3)  NOT NULL,
    `updated_at`        DATETIME(3)  NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_db_connection_name` (`name`),
    KEY `idx_db_connection_db_type` (`db_type`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '数据库连接配置';
