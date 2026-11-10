-- =============================================================
--  图书管理系统 4.0 —— 在线阅读建表脚本
--  数据库：library_system（MySQL 8.0）
--  执行方式：mysql -uroot -p123456 --default-character-set=utf8mb4 < 本文件
--           或在 IDEA 的数据库面板里右键本文件 -> Run
--  说明：本书是对 3.0 的增量升级，不动 1.0 的 book_category、
--        也不动 2.0 的 sys_user、3.0 的 notice / notice_read / borrow_record
--
--  ⚠️ 本脚本里有一句 ALTER TABLE，MySQL 8.0 的 ADD COLUMN
--     不支持 IF NOT EXISTS（那是 MariaDB 的特性），
--     所以【只能执行一次】。重复执行会报
--         ERROR 1060 (42S21): Duplicate column name 'readable'
--     这不是出错，是已经加过了，可以忽略并继续执行后面的 CREATE TABLE。
-- =============================================================

USE `library_system`;

-- -------------------------------------------------------------
-- 1. 图书表加一列：是否开启在线阅读
--    老数据自动变 0（未开启），不需要补数据。
--    注意：NOT NULL 必须带 DEFAULT，否则加列会失败。
-- -------------------------------------------------------------
ALTER TABLE `book`
    ADD COLUMN `readable` TINYINT(1) NOT NULL DEFAULT 0
        COMMENT '1=开启在线阅读，只有管理员能改' AFTER `status`;

-- -------------------------------------------------------------
-- 2. 图书正文表（冷热分离：正文单独存，列表页永远查不到它）
--    主键就是 book_id —— 一本书只有一份正文，天然唯一，
--    省掉一个自增 id 列。
--    content 用 LONGTEXT（上限 4GB），TEXT 只有 64KB 会不够。
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `book_content`
(
    `book_id`     INT      NOT NULL COMMENT '主键即 book.id',
    `content`     LONGTEXT NOT NULL COMMENT '正文纯文本（换行已统一成 \n）',
    `word_count`  INT      NOT NULL DEFAULT 0 COMMENT '去掉空白后的字符数',
    `encoding`    VARCHAR(16)       DEFAULT NULL COMMENT '检测出的编码，排错用',
    `create_time` DATETIME          DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME          DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`book_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='图书正文表';

-- -------------------------------------------------------------
-- 3. 读者上传文件表
--    所有查询都必须带 user_id 条件，否则 A 能看到 B 的文件。
--    chapter_count 必须存：列表页要显示"42 章"，
--    如果每次现切，翻一页就要把若干个文件的正文全扫一遍。
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `reader_file`
(
    `id`            BIGINT       NOT NULL AUTO_INCREMENT,
    `user_id`       INT          NOT NULL COMMENT '上传者（sys_user.id）',
    `title`         VARCHAR(200) NOT NULL COMMENT '显示名，默认取文件名（去扩展名）',
    `content`       LONGTEXT     NOT NULL COMMENT '正文纯文本',
    `word_count`    INT          NOT NULL DEFAULT 0,
    `chapter_count` INT          NOT NULL DEFAULT 0 COMMENT '切出几章，列表页要显示',
    `encoding`      VARCHAR(16)           DEFAULT NULL,
    `create_time`   DATETIME              DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_user` (`user_id`, `create_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='读者上传文件表';

-- -------------------------------------------------------------
-- 4. 阅读进度表（一张表两用：书籍和用户文件都记进度）
--    UNIQUE KEY uk_progress 是这张表的灵魂：
--    保证"一个用户对一个阅读目标只有一条进度"，
--    这样写进度才能用 INSERT ... ON DUPLICATE KEY UPDATE 一条 SQL 搞定，
--    不用先 SELECT 再决定 INSERT 还是 UPDATE（那在并发下会插出重复记录）。
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `reader_progress`
(
    `id`            BIGINT        NOT NULL AUTO_INCREMENT,
    `user_id`       INT           NOT NULL,
    `target_type`   TINYINT       NOT NULL COMMENT '1=书籍 2=用户文件',
    `target_id`     BIGINT        NOT NULL COMMENT '书籍填 book.id，文件填 reader_file.id',
    `chapter_index` INT           NOT NULL DEFAULT 0,
    `chapter_title` VARCHAR(200)           DEFAULT NULL,
    `percent`       DECIMAL(5, 2) NOT NULL DEFAULT 0 COMMENT '章节内滚动百分比 0~100',
    `update_time`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_progress` (`user_id`, `target_type`, `target_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='阅读进度表';

-- =============================================================
--  执行完怎么验证（把下面三段单独复制到控制台跑）
-- =============================================================
--
-- -- 1) book 是否多了 readable 列（期望 1 行）
-- SELECT column_name, column_type, column_default
-- FROM information_schema.columns
-- WHERE table_schema = 'library_system'
--   AND table_name = 'book' AND column_name = 'readable';
--
-- -- 2) 三张新表是否都在（期望 3 行）
-- SELECT table_name FROM information_schema.tables
-- WHERE table_schema = 'library_system'
--   AND table_name IN ('book_content', 'reader_file', 'reader_progress');
--
-- -- 3) 现有图书的 readable 是否都是 0（期望 readable 全是 0）
-- SELECT id, name, readable FROM book LIMIT 10;
--
-- =============================================================
--  注意：book.id 在 1.0 里是 BIGINT，而 book_content.book_id 这里写的是 INT
--  （接口文档原文如此）。INT 上限 21 亿，图书 id 不可能超，功能上没问题，
--  但严格说两个列类型不一致。要统一的话可以把上面改成 BIGINT，
--  同时把 BookContent.java 里的 bookId 从 Integer 改成 Long。
-- =============================================================
