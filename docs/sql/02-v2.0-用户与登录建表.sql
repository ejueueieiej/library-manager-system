-- =============================================================
--  图书管理系统 2.0 —— 用户 / 登录 建表脚本
--  数据库：library_system（MySQL 8.0）
--  执行方式：mysql -uroot -p123456 --default-character-set=utf8mb4 < 本文件
--           或在 IDEA 数据库面板里右键本文件 -> Run
--  说明：全部 CREATE TABLE IF NOT EXISTS，可重复执行，不动 1.0 的 book 表
-- =============================================================

USE `library_system`;

-- -------------------------------------------------------------
-- 用户表
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_user`
(
    `id`          BIGINT AUTO_INCREMENT COMMENT '用户id',
    `username`    VARCHAR(50)  NOT NULL COMMENT '登录名',
    `password`    VARCHAR(100) NOT NULL COMMENT '密码（BCrypt 哈希，不是明文！）',
    `real_name`   VARCHAR(50)           DEFAULT NULL COMMENT '姓名',
    `phone`       VARCHAR(20)           DEFAULT NULL COMMENT '手机号',
    `email`       VARCHAR(100)          DEFAULT NULL COMMENT '邮箱',
    `role`        VARCHAR(20)  NOT NULL DEFAULT 'USER' COMMENT '角色：ADMIN管理员 / USER普通用户',
    `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1正常 0禁用',
    `avatar`      VARCHAR(255)          DEFAULT NULL COMMENT '头像地址',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_username` (`username`),
    KEY `idx_user_role` (`role`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='系统用户表';

-- -------------------------------------------------------------
-- 初始化账号
--
-- ⚠️ 下面三串密码是 BCrypt 哈希，对应明文都是 123456。
--    不是明文存储 —— 数据库被人看到也反推不出原密码。
--    这三组哈希在生成时逐个用 BCrypt.checkpw("123456", hash) 验证过，结果为 true。
--
--    想再加账号：用 hutool 的 BCrypt.hashpw("密码", BCrypt.gensalt()) 生成，
--    千万不要自己手写哈希，也不要把明文塞进来。
-- -------------------------------------------------------------
INSERT INTO `sys_user` (`id`, `username`, `password`, `real_name`, `phone`, `role`, `status`)
VALUES (1, 'admin', '$2a$10$hgev1bbNOC3wvjsP4GMR4OJOwVp/maxciP50qc/XRmPz01J/jHjlm', '系统管理员', '13800000001', 'ADMIN', 1),
       (2, 'zhangsan', '$2a$10$dwPpdtqccmNeG3jc9VUcpObW2KgaBaFFEPsLoXp7/M5VpZzSES0cO', '张三', '13800000002', 'USER', 1),
       (3, 'lisi', '$2a$10$AZCN4lRY3854vMAbxJJBUuelVUPl6O2pLQG2jAZ4J22XAoQkxKRju', '李四', '13800000003', 'USER', 1)
ON DUPLICATE KEY UPDATE `real_name` = VALUES(`real_name`),
                        `phone`     = VALUES(`phone`),
                        `role`      = VALUES(`role`),
                        `status`    = VALUES(`status`);

-- -------------------------------------------------------------
-- 两个现成的测试账号（明文密码都是 123456）
--   admin     / 123456   → 管理员：能增删改查、能上下架、能管用户
--   zhangsan  / 123456   → 普通用户：只能查询和查看详情
-- -------------------------------------------------------------

-- 验证
-- SELECT id, username, real_name, role, status FROM sys_user;
