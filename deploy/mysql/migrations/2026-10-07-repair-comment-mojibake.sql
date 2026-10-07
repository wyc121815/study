-- 修复历史库里的中文注释乱码。可重复执行。
--
-- 背景：mysql 客户端的连接字符集默认是 latin1（MySQL 的 latin1 实际是 cp1252），
-- 早期用 docker exec / docker-entrypoint-initdb.d 灌 01-schema.sql 时没声明字符集，
-- 脚本里 UTF-8 的中文被当成 cp1252 解码后又存成 utf8mb4，等于编码了两次：
--   数据库连接配置 -> æ•°æ®åº“è¿žæŽ¥é…ç½®
-- 表注释、字段注释都中招（业务数据走 JDBC，不受影响）。
--
-- 修法：把上面的过程反过来走一遍。因为 MySQL 的 latin1 就是 cp1252，
-- CONVERT(x USING latin1) 取回原始字节，再按 utf8mb4 解释，就还原了。
--
-- 幂等：只处理"整字节位置存在 C3"的注释——cp1252 的 å/æ/ç 编成 UTF-8 都以 C3 开头，
-- 所以中文被双重编码后必然含 C3 这个字节；而正确的中文是 E4-E9 开头、后续都是
-- 80-BF 的续字节，不会出现 C3。修好后再跑一次会直接跳过，不会把好的注释又转坏。
-- 判据必须按"整字节"匹配：注释可能以 ASCII 开头（比如 "SQL 查询历史"），
-- 只看开头两个字符会把这种漏掉。
--
-- 用法（容器里执行）：
--   docker exec -i conn-platform-mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" < 2026-10-07-repair-comment-mojibake.sql

SET NAMES utf8mb4;

-- 存储过程必须归属于某个 schema，而修复语句里都带了库名前缀，选哪个库都行
USE `platform_conn`;

DROP PROCEDURE IF EXISTS `repair_mojibake_comments`;

DELIMITER $$
CREATE PROCEDURE `repair_mojibake_comments`()
BEGIN
    DECLARE done INT DEFAULT 0;
    DECLARE ddl LONGTEXT;

    -- 表注释：改表选项即可，不动列定义
    DECLARE cur_table CURSOR FOR
        SELECT CONCAT('ALTER TABLE `', TABLE_SCHEMA, '`.`', TABLE_NAME, '` COMMENT = ',
                      QUOTE(CONVERT(BINARY(CONVERT(TABLE_COMMENT USING latin1)) USING utf8mb4)), ';')
          FROM information_schema.TABLES
         WHERE TABLE_SCHEMA IN ('platform_auth', 'platform_conn', 'platform_demo')
           AND TABLE_COMMENT <> ''
           AND HEX(TABLE_COMMENT) REGEXP '^(..)*C3';

    -- 字段注释：MySQL 没有"只改注释"的语法，必须 MODIFY，
    -- 所以列定义全部从 information_schema 原样拼回来，只换 COMMENT
    DECLARE cur_column CURSOR FOR
        SELECT CONCAT('ALTER TABLE `', TABLE_SCHEMA, '`.`', TABLE_NAME, '` MODIFY COLUMN `', COLUMN_NAME, '` ',
                      COLUMN_TYPE,
                      IF(IS_NULLABLE = 'NO', ' NOT NULL', ' NULL'),
                      IF(COLUMN_DEFAULT IS NULL, '', CONCAT(' DEFAULT ', QUOTE(COLUMN_DEFAULT))),
                      IF(EXTRA = '', '', CONCAT(' ', EXTRA)),
                      ' COMMENT ',
                      QUOTE(CONVERT(BINARY(CONVERT(COLUMN_COMMENT USING latin1)) USING utf8mb4)),
                      ';')
          FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA IN ('platform_auth', 'platform_conn', 'platform_demo')
           AND COLUMN_COMMENT <> ''
           AND HEX(COLUMN_COMMENT) REGEXP '^(..)*C3'
         ORDER BY TABLE_SCHEMA, TABLE_NAME, ORDINAL_POSITION;

    DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = 1;

    OPEN cur_table;
    table_loop: LOOP
        FETCH cur_table INTO ddl;
        IF done = 1 THEN LEAVE table_loop; END IF;
        SET @stmt = ddl;
        PREPARE prepared FROM @stmt;
        EXECUTE prepared;
        DEALLOCATE PREPARE prepared;
    END LOOP;
    CLOSE cur_table;

    SET done = 0;
    OPEN cur_column;
    column_loop: LOOP
        FETCH cur_column INTO ddl;
        IF done = 1 THEN LEAVE column_loop; END IF;
        SET @stmt = ddl;
        PREPARE prepared FROM @stmt;
        EXECUTE prepared;
        DEALLOCATE PREPARE prepared;
    END LOOP;
    CLOSE cur_column;
END$$

DELIMITER ;

CALL `repair_mojibake_comments`();

DROP PROCEDURE `repair_mojibake_comments`;
