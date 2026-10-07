-- 批量导出任务表。可重复执行。
--
-- 用法（容器里执行）：
--   docker exec -i conn-platform-mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" < 2026-10-07-export-tasks.sql

-- mysql 客户端默认连接字符集是 latin1，不声明的话脚本里的中文注释会乱码
SET NAMES utf8mb4;

USE `platform_conn`;

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
