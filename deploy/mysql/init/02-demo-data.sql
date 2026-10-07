-- 指标平台的演示数据：一个独立的 platform_demo 库 + 只读账号 + 120 天销售明细。
--
-- 目的：让 SQL 查询台和指标图表一上手就有真实形状的数据可看，而不是空表。
-- 这个库和平台的业务库完全隔离，demo_reader 只有 SELECT 权限。

-- 必须放在最前面：mysql 客户端默认连接字符集是 latin1，
-- 不声明的话下面 sales_daily 里的中文（华东/华北/华南）会被双重编码成乱码。
SET NAMES utf8mb4;

CREATE DATABASE IF NOT EXISTS `platform_demo`
    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;

CREATE USER IF NOT EXISTS 'demo_reader'@'%' IDENTIFIED BY 'demo123';
GRANT SELECT ON `platform_demo`.* TO 'demo_reader'@'%';
FLUSH PRIVILEGES;

USE `platform_demo`;

-- 唯一键 (stat_date, channel, region) 让下面的 INSERT IGNORE 天然幂等：
-- 脚本重复执行只会插入缺失的那几天，不会产生重复数据。
CREATE TABLE IF NOT EXISTS `sales_daily`
(
    `id`         BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `stat_date`  DATE          NOT NULL COMMENT '统计日期',
    `channel`    VARCHAR(16)   NOT NULL COMMENT '渠道 WECHAT/ALIPAY/CARD',
    `region`     VARCHAR(16)   NOT NULL COMMENT '大区',
    `order_cnt`  INT           NOT NULL COMMENT '下单数',
    `pay_cnt`    INT           NOT NULL COMMENT '支付成功数',
    `amount`     DECIMAL(14,2) NOT NULL COMMENT '支付金额（元）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_sales_daily` (`stat_date`, `channel`, `region`),
    KEY `idx_sales_daily_date` (`stat_date`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '演示：每日销售明细';

-- 最近 120 天 × 6 个渠道/大区组合。数值用日期和 seed 推导，保证可重复。
INSERT IGNORE INTO `sales_daily` (`stat_date`, `channel`, `region`, `order_cnt`, `pay_cnt`, `amount`)
WITH RECURSIVE `seq` (`n`) AS (SELECT 0 UNION ALL SELECT `n` + 1 FROM `seq` WHERE `n` < 119),
`dim` AS (SELECT 'WECHAT' AS `channel`, '华东' AS `region`, 120 AS `base_orders`, 62.5 AS `avg_price`, 3 AS `seed`
          UNION ALL SELECT 'WECHAT', '华北', 90, 58.0, 7
          UNION ALL SELECT 'ALIPAY', '华东', 105, 71.0, 11
          UNION ALL SELECT 'ALIPAY', '华南', 84, 66.5, 5
          UNION ALL SELECT 'CARD', '华北', 46, 158.0, 13
          UNION ALL SELECT 'CARD', '华南', 38, 149.0, 17),
`orders` AS (SELECT DATE_SUB(CURDATE(), INTERVAL `seq`.`n` DAY) AS `stat_date`,
                    `dim`.`channel`,
                    `dim`.`region`,
                    `dim`.`avg_price`,
                    `dim`.`seed`,
                    GREATEST(`dim`.`base_orders`
                                 + ((`seq`.`n` * 17 + `dim`.`seed` * 29) % 41) - 15
                                 + IF(DAYOFWEEK(DATE_SUB(CURDATE(), INTERVAL `seq`.`n` DAY)) IN (1, 7), -18, 14),
                             5) AS `order_cnt`
             FROM `seq`
                      CROSS JOIN `dim`),
`paid` AS (SELECT `stat_date`,
                  `channel`,
                  `region`,
                  `avg_price`,
                  `order_cnt`,
                  GREATEST(`order_cnt` - ((`order_cnt` * (6 + (`seed` % 6))) / 100), 1) AS `pay_cnt`
           FROM `orders`)
SELECT `stat_date`,
       `channel`,
       `region`,
       `order_cnt`,
       `pay_cnt`,
       ROUND(`pay_cnt` * `avg_price` * (0.97 + ((`order_cnt` % 9) / 100)), 2) AS `amount`
FROM `paid`;
