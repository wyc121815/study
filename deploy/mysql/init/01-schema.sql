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
    `query_enabled`     TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否允许用于 SQL/指标查询，0=仅管理',
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

-- 查询历史：每次 SQL 执行都落一条，既是审计也是"最近查询"的数据源。
-- 不建外键，连接被删后历史仍保留（快照连接名）。
CREATE TABLE IF NOT EXISTS `query_history`
(
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`         BIGINT       NULL COMMENT '执行人用户 ID',
    `username`        VARCHAR(64)  NULL COMMENT '执行人登录名快照',
    `connection_id`   BIGINT       NULL COMMENT '目标连接 ID',
    `connection_name` VARCHAR(128) NULL COMMENT '目标连接名快照',
    `sql_text`        TEXT         NOT NULL COMMENT '执行的 SQL',
    `source`          VARCHAR(32)  NOT NULL DEFAULT 'ADHOC' COMMENT 'ADHOC=查询台 / METRIC=指标',
    `statement_type`  VARCHAR(32)  NULL COMMENT '语句类型',
    `row_count`       INT          NULL COMMENT '返回行数',
    `elapsed_ms`      BIGINT       NULL COMMENT '耗时（毫秒）',
    `success`         TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否成功',
    `message`         VARCHAR(512) NULL COMMENT '失败原因或截断提示',
    `created_at`      DATETIME(3)  NOT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_query_history_user_time` (`user_id`, `created_at`),
    KEY `idx_query_history_time` (`created_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT 'SQL 查询历史';

-- 指标定义：P1 先承载"把查询台的 SQL 存下来复用"，P2 再补参数与输出契约。
CREATE TABLE IF NOT EXISTS `metric_definition`
(
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `name`          VARCHAR(128) NOT NULL COMMENT '指标名称',
    `description`   VARCHAR(512) NULL COMMENT '口径说明',
    `datasource_id` BIGINT       NOT NULL COMMENT '指向 db_connection.id',
    `sql_text`      TEXT         NOT NULL COMMENT 'SQL，P2 起支持命名参数',
    `status`        VARCHAR(16)  NOT NULL DEFAULT 'ENABLED' COMMENT 'ENABLED/DISABLED',
    `created_by`    BIGINT       NULL COMMENT '创建人用户 ID',
    `created_at`    DATETIME(3)  NOT NULL,
    `updated_at`    DATETIME(3)  NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_metric_definition_name` (`name`),
    KEY `idx_metric_definition_datasource` (`datasource_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '指标定义';

-- 批量导出任务：接口落一条 PENDING 记录并把 taskId 投进队列，消费者跑完把
-- CSV 写回 content，前端轮询状态后下载。PENDING->RUNNING 的更新是 CAS 认领，
-- 用来抵消队列"至少一次投递"带来的重复消费。
CREATE TABLE IF NOT EXISTS `query_export_task`
(
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `task_id`         CHAR(32)     NOT NULL COMMENT '对外任务号（UUID hex）',
    `user_id`         BIGINT       NULL COMMENT '提交人',
    `username`        VARCHAR(64)  NULL COMMENT '提交人登录名快照',
    `connection_id`   BIGINT       NOT NULL COMMENT '目标连接 ID',
    `connection_name` VARCHAR(128) NULL COMMENT '目标连接名快照',
    `sql_text`        TEXT         NOT NULL COMMENT '导出的 SQL',
    `max_rows`        INT          NULL COMMENT '行数上限',
    `source`          VARCHAR(32)  NOT NULL DEFAULT 'ADHOC' COMMENT 'ADHOC/METRIC',
    `status`          VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/RUNNING/DONE/FAILED',
    `row_count`       INT          NULL COMMENT '导出行数',
    `file_name`       VARCHAR(160) NULL COMMENT '下载文件名',
    `content`         MEDIUMTEXT   NULL COMMENT 'CSV 正文（含 BOM）',
    `message`         VARCHAR(512) NULL COMMENT '失败原因或截断提示',
    `created_at`      DATETIME(3)  NOT NULL,
    `updated_at`      DATETIME(3)  NOT NULL,
    `finished_at`     DATETIME(3)  NULL,
    `expires_at`      DATETIME(3)  NULL COMMENT '结果保留截止时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_query_export_task_task_id` (`task_id`),
    KEY `idx_query_export_task_user` (`user_id`, `created_at`),
    KEY `idx_query_export_task_status` (`status`, `updated_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '批量导出任务';
