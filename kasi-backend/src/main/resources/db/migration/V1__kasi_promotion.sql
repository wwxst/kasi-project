-- =========================================================
-- V1: 初始化用户表
-- =========================================================
--
-- 使用前请先手动创建数据库：
--   CREATE DATABASE IF NOT EXISTS `kasi_promotion`
--       DEFAULT CHARACTER SET utf8mb4
--       DEFAULT COLLATE utf8mb4_0900_ai_ci;
--

-- 后台管理员表
CREATE TABLE `sys_admin_user`
(
    `id`                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `username`            VARCHAR(64)     NOT NULL COMMENT '登录账号',
    `password`            VARCHAR(255)    NOT NULL COMMENT '密码（BCrypt加密）',
    `real_name`           VARCHAR(64)     NOT NULL COMMENT '真实姓名',
    `mobile`              VARCHAR(32)              DEFAULT NULL COMMENT '手机号',
    `email`               VARCHAR(128)             DEFAULT NULL COMMENT '邮箱',
    `avatar_url`          VARCHAR(512)             DEFAULT NULL COMMENT '头像',
    `department_id`       BIGINT UNSIGNED          DEFAULT NULL COMMENT '部门ID',
    `status`              TINYINT         NOT NULL DEFAULT 1 COMMENT '状态：0禁用 1正常',
    `is_super_admin`      TINYINT         NOT NULL DEFAULT 0 COMMENT '是否超级管理员：0否 1是',
    `last_login_at`       DATETIME                 DEFAULT NULL COMMENT '最后登录时间',
    `last_login_ip`       VARCHAR(64)              DEFAULT NULL COMMENT '最后登录IP',
    `password_changed_at` DATETIME                 DEFAULT NULL COMMENT '最后修改密码时间',
    `remark`              VARCHAR(500)             DEFAULT NULL COMMENT '备注',
    `created_by`          BIGINT UNSIGNED          DEFAULT NULL COMMENT '创建人',
    `updated_by`          BIGINT UNSIGNED          DEFAULT NULL COMMENT '更新人',
    `created_at`          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_admin_username` (`username`),
    UNIQUE KEY `uk_admin_mobile` (`mobile`),
    UNIQUE KEY `uk_admin_email` (`email`),
    KEY `idx_admin_status` (`status`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='后台管理员用户';

-- 推广用户表
CREATE TABLE `promotion_user`
(
    `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_no`         VARCHAR(32)     NOT NULL COMMENT '业务用户编号，如KS000001',
    `password`        VARCHAR(255)    NOT NULL COMMENT '密码（BCrypt加密）',
    `nickname`        VARCHAR(64)              DEFAULT NULL COMMENT '昵称',
    `real_name`       VARCHAR(64)              DEFAULT NULL COMMENT '真实姓名',
    `mobile`          VARCHAR(32)              DEFAULT NULL COMMENT '手机号',
    `email`           VARCHAR(128)             DEFAULT NULL COMMENT '邮箱',
    `avatar_url`      VARCHAR(512)             DEFAULT NULL COMMENT '头像',
    `status`          TINYINT         NOT NULL DEFAULT 1 COMMENT '状态：0禁用 1正常',
    `register_source` VARCHAR(32)              DEFAULT NULL COMMENT '注册来源',
    `last_login_at`   DATETIME                 DEFAULT NULL COMMENT '最后登录时间',
    `last_login_ip`   VARCHAR(64)              DEFAULT NULL COMMENT '最后登录IP',
    `remark`          VARCHAR(500)             DEFAULT NULL COMMENT '备注',
    `created_at`      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_no` (`user_no`),
    UNIQUE KEY `uk_mobile` (`mobile`),
    UNIQUE KEY `uk_email` (`email`),
    KEY `idx_status` (`status`),
    KEY `idx_created_at` (`created_at`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='推广用户';
