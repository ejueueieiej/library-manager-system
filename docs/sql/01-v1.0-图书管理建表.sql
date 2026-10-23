-- =============================================================
--  图书管理系统 1.0 —— 建表脚本（物理删除版）
--  数据库：library_system（MySQL 8.0）
--  执行方式：mysql -uroot -p123456 --default-character-set=utf8mb4 -e "source 本文件路径"
--           或在 IDEA 的数据库面板里右键本文件 -> Run
--  说明：全部使用 CREATE TABLE IF NOT EXISTS，可重复执行，不会删你已有的数据
-- =============================================================

CREATE DATABASE IF NOT EXISTS `library_system`
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_general_ci;

USE `library_system`;

-- -------------------------------------------------------------
-- 1. 图书分类表
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `book_category`
(
    `id` INT AUTO_INCREMENT COMMENT '分类id',
    `name`        VARCHAR(50) NOT NULL COMMENT '分类名称',
    `sort`        INT         NOT NULL DEFAULT 0 COMMENT '排序号，越小越靠前',
    `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_category_name` (`name`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='图书分类表';

-- -------------------------------------------------------------
-- 2. 图书表
--    删除方式：物理删除（DELETE FROM），所以没有 deleted 字段
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `book`
(
    `id`            BIGINT AUTO_INCREMENT COMMENT '图书id',
    `isbn`          VARCHAR(20)    NOT NULL COMMENT 'ISBN 书号',
    `name`          VARCHAR(100)   NOT NULL COMMENT '书名',
    `author`        VARCHAR(50)    NOT NULL COMMENT '作者',
    `publisher`     VARCHAR(100)            DEFAULT NULL COMMENT '出版社',
    `publish_date`  DATE                    DEFAULT NULL COMMENT '出版日期',
    `category_id`   INT            NOT NULL COMMENT '分类id（关联 book_category.id）',
    `price`         DECIMAL(10, 2) NOT NULL DEFAULT 0.00 COMMENT '定价',
    `total_num`     INT            NOT NULL DEFAULT 1 COMMENT '总册数',
    `available_num` INT            NOT NULL DEFAULT 1 COMMENT '在馆可借册数（给 2.0 借阅功能预留）',
    `cover_url`     VARCHAR(255)            DEFAULT NULL COMMENT '封面图地址',
    `intro`         TEXT COMMENT '简介',
    `status`        TINYINT        NOT NULL DEFAULT 1 COMMENT '状态：1在架 0下架',
    `create_time`   DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    -- ★ 物理删除下可以放心加唯一索引：删掉的书真的从表里消失，
    --   同一本 ISBN 重新录入不会冲突。这样「ISBN 不能重复」由数据库兜底，最可靠。
    UNIQUE KEY `uk_book_isbn` (`isbn`),
    KEY `idx_book_name` (`name`),
    KEY `idx_book_author` (`author`),
    KEY `idx_book_category` (`category_id`),
    KEY `idx_book_status` (`status`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='图书表';

-- 【物理删除的注意事项（写给以后的自己）】
-- 物理删除 = DELETE FROM book WHERE id = ?
-- 好处：简单、表不会越用越大、ISBN 可以加唯一索引。
-- 代价：数据没了就是没了，误删只能靠备份恢复。
-- 以后 2.0 加「借阅记录」表时会出现一个绕不开的问题：
--   如果某本书还有借阅记录（甚至还没还），直接物理删除会把借阅记录变成孤儿数据。
--   推荐做法（等 2.0 再实现）：删除前先查有没有未归还的借阅记录，
--   有就返回「该图书还有未归还的借阅，不能删除」，没有才真删。

-- -------------------------------------------------------------
-- 3. 初始化分类数据
-- -------------------------------------------------------------
INSERT INTO `book_category` (`id`, `name`, `sort`)
VALUES (1, '文学', 1),
       (2, '计算机', 2),
       (3, '历史', 3),
       (4, '科学', 4),
       (5, '艺术', 5),
       (6, '教育', 6)
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `sort` = VALUES(`sort`);

-- -------------------------------------------------------------
-- 4. 初始化图书数据（方便你接口一写完就能看到效果）
--    因为有 isbn 唯一索引，重复执行只会更新不会插入重复数据
-- -------------------------------------------------------------
INSERT INTO `book` (`isbn`, `name`, `author`, `publisher`, `publish_date`, `category_id`, `price`,
                    `total_num`, `available_num`, `cover_url`, `intro`, `status`)
VALUES ('9787020002207', '红楼梦', '曹雪芹', '人民文学出版社', '1996-12-01', 1, 59.70, 5, 5, NULL,
        '中国古典四大名著之一，以贾宝玉与林黛玉的爱情悲剧为主线。', 1),
       ('9787115428028', 'Java 核心技术 卷I', '凯 S. 霍斯特曼', '人民邮电出版社', '2016-09-01', 2, 149.00, 3, 3, NULL,
        'Java 领域的经典入门与进阶教程，覆盖 Java 语言核心 API。', 1),
       ('9787111213826', '深入理解计算机系统', 'Randal E. Bryant', '机械工业出版社', '2016-11-01', 2, 139.00, 2, 2, NULL,
        '从程序员视角讲解计算机系统，CSAPP 中文版。', 1),
       ('9787508647357', '人类简史', '尤瓦尔·赫拉利', '中信出版社', '2014-11-01', 3, 68.00, 4, 4, NULL,
        '从认知革命到科学革命，重新理解人类的过去与未来。', 1),
       ('9787535732309', '时间简史', '史蒂芬·霍金', '湖南科学技术出版社', '2010-04-01', 4, 45.00, 3, 2, NULL,
        '关于宇宙起源、黑洞与时间本质的科普经典。', 1),
       ('9787108032225', '艺术的故事', '贡布里希', '生活·读书·新知三联书店', '2008-04-01', 5, 280.00, 1, 1, NULL,
        '西方艺术史入门必读书，被誉为艺术史的圣经。', 1),
       ('9787107249020', '给教师的建议', '苏霍姆林斯基', '教育科学出版社', '1984-06-01', 6, 39.00, 6, 6, NULL,
        '面向一线教师的教育学经典，实操性极强。', 1),
       ('9787121362217', 'Vue.js 设计与实现', '霍春阳', '人民邮电出版社', '2022-01-01', 2, 119.00, 2, 2, NULL,
        '深入剖析 Vue 3 的响应式系统、渲染器与编译器实现原理。', 1)
ON DUPLICATE KEY UPDATE `name`          = VALUES(`name`),
                        `author`        = VALUES(`author`),
                        `publisher`     = VALUES(`publisher`),
                        `publish_date`  = VALUES(`publish_date`),
                        `category_id`   = VALUES(`category_id`),
                        `price`         = VALUES(`price`),
                        `total_num`     = VALUES(`total_num`),
                        `available_num` = VALUES(`available_num`),
                        `intro`         = VALUES(`intro`),
                        `status`        = VALUES(`status`);

-- -------------------------------------------------------------
-- 5. 验证
-- -------------------------------------------------------------
-- SELECT * FROM book_category ORDER BY sort;
-- SELECT b.*, c.name AS category_name FROM book b LEFT JOIN book_category c ON b.category_id = c.id;
