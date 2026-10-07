-- 登录鉴权加固：为存量库补齐字段与表。
--
-- 全新初始化的库直接用 init/01-schema.sql 即可，本脚本只针对"数据卷已存在、
-- 不想重建丢数据"的旧库。可重复执行。
--
-- 用法（容器里执行）：
--   docker exec -i conn-platform-mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" < 2026-10-07-auth-hardening.sql
-- 本地开发库同理，把容器名/密码换成自己的。

USE `platform_auth`;

-- sys_user.failed_attempts：连续登录失败次数
SET @exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = 'platform_auth'
                  AND TABLE_NAME = 'sys_user'
                  AND COLUMN_NAME = 'failed_attempts');
SET @sql := IF(@exists = 0,
    'ALTER TABLE `sys_user` ADD COLUMN `failed_attempts` INT NOT NULL DEFAULT 0 COMMENT ''连续登录失败次数'' AFTER `status`',
    'DO 0');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- sys_user.locked_until：锁定截止时间
SET @exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = 'platform_auth'
                  AND TABLE_NAME = 'sys_user'
                  AND COLUMN_NAME = 'locked_until');
SET @sql := IF(@exists = 0,
    'ALTER TABLE `sys_user` ADD COLUMN `locked_until` DATETIME(3) NULL COMMENT ''锁定截止时间，空=未锁定'' AFTER `failed_attempts`',
    'DO 0');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- auth_refresh_token：刷新令牌
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
