-- SQL 查询与指标平台：为存量库补齐字段与表。可重复执行。
--
-- 用法（容器里执行）：
--   docker exec -i conn-platform-mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" < 2026-10-07-query-platform.sql
-- 本地开发库同理，把容器名/密码换成自己的。

-- mysql 客户端默认连接字符集是 latin1，不声明的话脚本里的中文注释会乱码
SET NAMES utf8mb4;

USE `platform_conn`;

-- db_connection.query_enabled：控制该连接是否允许被用于 SQL / 指标查询。
-- 平台自身的连接库建议置 0，避免通过查询台读到平台凭据。
SET @exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = 'platform_conn'
                  AND TABLE_NAME = 'db_connection'
                  AND COLUMN_NAME = 'query_enabled');
SET @sql := IF(@exists = 0,
    'ALTER TABLE `db_connection` ADD COLUMN `query_enabled` TINYINT(1) NOT NULL DEFAULT 1 COMMENT ''是否允许用于 SQL/指标查询，0=仅管理'' AFTER `remark`',
    'DO 0');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

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

-- 平台自身的连接库不对外提供查询，避免通过查询台读到 password_cipher。
UPDATE `db_connection` SET `query_enabled` = 0 WHERE `database_name` = 'platform_conn';
