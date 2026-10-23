-- =============================================================
--  图书管理系统 3.0 —— 通知公告 + 借阅归还 建表脚本
--  数据库：library_system（MySQL 8.0）
--  执行方式：mysql -uroot -p123456 --default-character-set=utf8mb4 < 本文件
--           或在 IDEA 数据库面板里右键本文件 -> Run
--  说明：全部 CREATE TABLE IF NOT EXISTS，可重复执行；
--        不动 1.0 的 book / book_category，也不动 2.0 的 sys_user
-- =============================================================

USE `library_system`;

-- =============================================================
--  第一部分：通知公告（3 张表里的前 2 张）
-- =============================================================

-- -------------------------------------------------------------
-- 1. 通知公告表
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `notice`
(
    `id`           BIGINT AUTO_INCREMENT COMMENT '通知id',
    `title`        VARCHAR(100) NOT NULL COMMENT '标题',
    `content`      TEXT         NOT NULL COMMENT '正文（纯文本，换行用换行符，前端按换行分段渲染）',
    `type`         TINYINT      NOT NULL DEFAULT 1 COMMENT '类型：1公告 2通知 3活动',
    `is_top`       TINYINT      NOT NULL DEFAULT 0 COMMENT '是否置顶：1置顶 0普通',
    `status`       TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1已发布 0已下架',
    `publisher_id` BIGINT       NOT NULL COMMENT '发布人（sys_user.id）',
    `publish_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '首次发布时间（编辑不改它）',
    `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后修改时间',
    PRIMARY KEY (`id`),
    -- ★ 置顶排序专用索引：正好对上 ORDER BY is_top DESC, publish_time DESC
    --   （WHERE 用不到它，但排序能吃；数据量小时看不出差别，属于"写对了不亏"）
    KEY `idx_notice_sort` (`is_top`, `publish_time`),
    KEY `idx_notice_status` (`status`),
    KEY `idx_notice_type` (`type`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='通知公告表';

-- 【关于 type 这个列名】
-- 有人会担心 type 是 SQL 关键字。在 MySQL 8 里 TYPE 属于「非保留关键字」，
-- 可以直接当列名用（上面的建表语句能跑通就是证明）。
-- 唯一的要求：写 SQL 时用反引号包一下 `type`，避免和别的东西看串。

-- 【为什么没有 deleted / 撤回状态？】
-- status 只有 1 已发布 / 0 已下架，没有第 3 个"草稿"状态。
-- 课程项目阶段不需要草稿箱：想先写不发布，就 status=0 存着，改好了再改成 1。
-- —— 其实这已经等价于草稿了，只是不额外叫这个名字。

-- -------------------------------------------------------------
-- 2. 通知已读表（"哪个用户读过哪篇通知"）
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `notice_read`
(
    `id`        BIGINT AUTO_INCREMENT COMMENT '主键',
    `notice_id` BIGINT   NOT NULL COMMENT '通知id（notice.id）',
    `user_id`   BIGINT   NOT NULL COMMENT '用户id（sys_user.id）',
    `read_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '首次阅读时间',
    PRIMARY KEY (`id`),
    -- ★★ 这个唯一索引是整张表的灵魂：
    --    靠它保证「同一个人 + 同一篇通知」只会有一行，
    --    于是插入时可以直接用 INSERT IGNORE —— 重复打开同一篇通知不会报错、也不会多出行。
    --    没有它的话，读 10 次就插 10 行，未读数统计立刻失真。
    UNIQUE KEY `uk_notice_user` (`notice_id`, `user_id`),
    -- 统计"我的未读数"时要按 user_id 过滤
    KEY `idx_read_user` (`user_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='通知已读记录表';

-- 【为什么不做成 notice 表里的一个 is_read 字段？】
-- 因为"已读"是【每个人各有一份】的状态，不是通知自己的属性。
-- 一篇通知发给 100 个读者，就有 100 份已读状态 —— 这是一张典型的多对多关系表。
-- 塞进 notice 表的话，只能记"最后一个人读没读"，语义直接崩了。

-- =============================================================
--  第二部分：借阅归还
-- =============================================================

-- -------------------------------------------------------------
-- 3. 借阅记录表
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `borrow_record`
(
    `id`          BIGINT AUTO_INCREMENT COMMENT '借阅id',
    `user_id`     BIGINT   NOT NULL COMMENT '借阅人（sys_user.id）',
    `book_id`     BIGINT   NOT NULL COMMENT '图书（book.id）',
    `borrow_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '借出时间',
    `due_time`    DATETIME NOT NULL COMMENT '应还时间（借出时间 + 30 天）',
    `return_time` DATETIME          DEFAULT NULL COMMENT '实际归还时间，未归还时为 NULL',
    `status`      TINYINT  NOT NULL DEFAULT 1 COMMENT '状态：1借出中 2已归还',
    `renew_count` INT      NOT NULL DEFAULT 0 COMMENT '已续借次数（上限 1 次）',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    -- 查「我的借阅」、判断「这人还有几本没还」都走这个索引
    KEY `idx_borrow_user` (`user_id`, `status`),
    -- 删书前的「还有没有没还的」检查、以及按书统计
    KEY `idx_borrow_book` (`book_id`, `status`),
    -- 逾期筛选：status = 1 AND due_time < NOW()
    KEY `idx_borrow_due` (`status`, `due_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='借阅记录表';

-- 【★★ 为什么没有 overdue（是否逾期）字段？】
-- 因为"逾期"会【随着时间自己变】，不是借出那一刻能定下来的事实。
--   今天没逾期，明天可能就逾期了 —— 除非你写个定时任务每天凌晨刷一遍，
--   而定时任务一旦没跑（服务停了、时间对不上），数据就和现实不一致了。
--
-- 正确做法：只存 status（1借出中 / 2已归还）+ due_time，逾期永远【现算】：
--     status = 1 AND due_time < NOW()   →  逾期
--   这样永远不会不一致，也不需要定时任务。
-- 后端返回给前端时，再顺手算一个 overdueDays（已逾期天数，0 表示没逾期）。

-- 【★★ 为什么 status 只有 1 和 2，没有"已逾期"，也没有"审批中"？】
-- 已逾期：见上，它是算出来的，不是存出来的。
-- 审批中：3.0 采用的是「自助借阅、立即借出」（你已确认），所以没有审批环节。
--         只有"借出中"和"已归还"两个状态，状态机最简单，也最不容易出 bug。

-- 【为什么不加 FOREIGN KEY 外键约束？】
-- 和 1.0/2.0 保持一致 —— 现有的 book.category_id、notice.publisher_id 都没有建外键，
-- 全靠应用层保证。好处是删数据不会被外键卡住、建表顺序无所谓；
-- 代价是可能出现孤儿数据（所以下面第 5 节有专门的"一致性自检"）。

-- =============================================================
--  第三部分：初始化数据
-- =============================================================

-- -------------------------------------------------------------
-- 4. 初始化 4 条通知（够你把列表、置顶、类型筛选、未读红点全试一遍）
--
--    写死 id 是为了【可重复执行】：第二次跑会走 ON DUPLICATE KEY UPDATE，
--    更新而不是插重复行。
--    publisher_id = 1 就是 admin（见 02-v2.0 建表脚本）。
-- -------------------------------------------------------------
INSERT INTO `notice` (`id`, `title`, `content`, `type`, `is_top`, `status`, `publisher_id`, `publish_time`)
VALUES (1, '图书馆国庆假期开放安排',
        '10 月 1 日至 3 日全馆闭馆，10 月 4 日起恢复正常开放。\n闭馆期间自助还书机暂停使用，到期图书请提前归还或办理续借。\n由此带来不便，敬请谅解。',
        1, 1, 1, 1, '2026-09-28 09:00:00'),
       (2, '新书上架：本月新增 42 册计算机类图书',
        '本月新到计算机类图书 42 册，涵盖 Java、前端、数据库与人工智能方向，现已全部上架可借。\n热门书目包括《Vue.js 设计与实现》《深入理解计算机系统》等，欢迎到馆借阅。',
        2, 0, 1, 1, '2026-10-08 14:20:00'),
       (3, '关于借阅规则调整的通知',
        '自即日起，每位读者同时可借图书上限调整为 5 册，借期 30 天，可续借 1 次（每次 30 天）。\n逾期未还将影响后续借阅，请留意自己的应还日期。',
        2, 0, 1, 1, '2026-10-10 10:05:00'),
       (4, '读书月活动预告：好书共读计划',
        '本月将举办"好书共读"活动，参与者可优先借阅指定书目，并在活动结束后获得借阅时长奖励。\n报名方式详见馆内公告栏，名额有限，先到先得。',
        3, 0, 1, 1, '2026-10-11 16:40:00')
ON DUPLICATE KEY UPDATE `title`        = VALUES(`title`),
                        `content`      = VALUES(`content`),
                        `type`         = VALUES(`type`),
                        `is_top`       = VALUES(`is_top`),
                        `status`       = VALUES(`status`),
                        `publisher_id` = VALUES(`publisher_id`),
                        `publish_time` = VALUES(`publish_time`);

-- -------------------------------------------------------------
-- 5. ★ 一致性自检 SQL（联调时怀疑"数据对不上"就跑这两条）
-- -------------------------------------------------------------

-- 5.1 图书的 available_num 是否和借阅记录对得上？
--     正常应该【查不出任何行】。查出来就说明有借出/归还没同步好。
--     should_be = 总册数 - 当前借出中的册数
-- SELECT b.id,
--        b.name,
--        b.total_num,
--        b.available_num,
--        b.total_num - (SELECT COUNT(*) FROM borrow_record r
--                       WHERE r.book_id = b.id AND r.status = 1) AS should_be
-- FROM book b
-- HAVING b.available_num <> should_be;

-- 5.2 有没有"孤儿已读记录"（通知已经删了，已读记录还在）？
--     正常应该为 0。
-- SELECT COUNT(*) AS orphan_read
-- FROM notice_read r
-- LEFT JOIN notice n ON n.id = r.notice_id
-- WHERE n.id IS NULL;

-- 5.3 有没有"孤儿借阅记录"（图书被删了，借阅记录还指着它）？
--     已归还的历史记录允许出现这种情况（见文档里的取舍说明），
--     但如果 status = 1（借出中）还能查到孤儿，说明删书前的校验漏了 —— 那是 bug。
-- SELECT r.*
-- FROM borrow_record r
-- LEFT JOIN book b ON b.id = r.book_id
-- WHERE b.id IS NULL AND r.status = 1;

-- -------------------------------------------------------------
-- 6. 验证：跑完上面脚本后，这几条应该都有结果
-- -------------------------------------------------------------
-- SELECT id, title, type, is_top, status, publisher_id FROM notice ORDER BY is_top DESC, publish_time DESC;
-- SELECT COUNT(*) AS notice_count FROM notice;   -- 期望 4
-- SELECT * FROM borrow_record;                   -- 期望 0 行（借阅记录靠你在页面上点出来）
