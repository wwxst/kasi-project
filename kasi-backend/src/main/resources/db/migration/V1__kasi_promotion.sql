-- =========================================================
-- Merged source: V1__kasi_promotion.sql
-- =========================================================
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
    `user_no`         CHAR(12)        NOT NULL COMMENT '12位随机数字业务用户编号',
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

-- 初始超级管理员（首次登录后应立即修改默认密码）
INSERT INTO `sys_admin_user` (`username`, `password`, `real_name`, `status`, `is_super_admin`)
VALUES ('admin',
        '$2a$10$eA5VNH.Ca3WaS1Z3.4fNxerBp75GO9qu.hzsRWiEQp6CU63fBn19u',
        '系统管理员',
        1,
        1);

-- 初始推广用户（首次登录后应立即修改密码）
INSERT INTO `promotion_user` (`user_no`, `password`, `email`, `status`)
VALUES ('191931716670',
        '$2a$10$kjoq..xthy8OTRI2/WNJLuVCA9lfmYIK3u9/PIgIZTbJ42nWJWllC',
        '19193171667@163.com',
        1);

-- =========================================================
-- Merged source: V2__media_account_filing.sql
-- =========================================================
-- =========================================================
-- V2: 平台接入、媒体账号与通用报备
-- =========================================================

-- 短剧平台定义。平台接入账号和报备记录均通过平台编码扩展。
CREATE TABLE `short_drama_provider`
(
    `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `provider_code` VARCHAR(32)     NOT NULL COMMENT '平台编码',
    `provider_name` VARCHAR(64)     NOT NULL COMMENT '平台名称',
    `status`        TINYINT         NOT NULL DEFAULT 1 COMMENT '状态：0禁用 1启用',
    `created_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_drama_provider_code` (`provider_code`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='短剧平台';

-- 平台机构接入账号。密钥只保存密文，不在本迁移中植入任何接入凭据。
CREATE TABLE `short_drama_connection`
(
    `id`                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `provider_id`        BIGINT UNSIGNED NOT NULL COMMENT '短剧平台ID',
    `connection_name`    VARCHAR(64)     NOT NULL COMMENT '接入账号名称',
    `partner_id`         VARCHAR(64)              DEFAULT NULL COMMENT '平台机构标识（API报备必填）',
    `api_key_ciphertext` TEXT                    DEFAULT NULL COMMENT '平台密钥密文（API报备必填）',
    `currency`           CHAR(3)         NOT NULL COMMENT 'ISO 4217币种',
    `status`             TINYINT         NOT NULL DEFAULT 1 COMMENT '状态：0禁用 1启用',
    `created_by`         BIGINT UNSIGNED          DEFAULT NULL COMMENT '创建管理员',
    `updated_by`         BIGINT UNSIGNED          DEFAULT NULL COMMENT '更新管理员',
    `created_at`         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_drama_connection_provider` (`provider_id`),
    CONSTRAINT `fk_drama_connection_provider`
        FOREIGN KEY (`provider_id`) REFERENCES `short_drama_provider` (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='短剧平台接入账号';

INSERT INTO `short_drama_provider` (`provider_code`, `provider_name`, `status`)
VALUES ('GOODSHORT', 'GoodShort', 1);

-- 推广用户绑定的媒体账号。账号不物理删除，归属用户不可转移。
CREATE TABLE `promotion_media_account`
(
    `id`                    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id`               BIGINT UNSIGNED NOT NULL COMMENT '推广用户ID',
    `media_type`            VARCHAR(32)     NOT NULL COMMENT '媒体平台编码',
    `external_account_id`   VARCHAR(128)    NOT NULL COMMENT '媒体平台账号ID',
    `account_name`          VARCHAR(128)             DEFAULT NULL COMMENT '账号名称',
    `account_link`          VARCHAR(512)             DEFAULT NULL COMMENT '账号主页链接',
    `status`                TINYINT         NOT NULL DEFAULT 1 COMMENT '状态：0停用 1启用',
    `data_version`          INT             NOT NULL DEFAULT 1 COMMENT '资料版本',
    `created_at`            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_media_account_platform_id` (`media_type`, `external_account_id`),
    KEY `idx_media_account_user_status` (`user_id`, `status`),
    CONSTRAINT `fk_media_account_user`
        FOREIGN KEY (`user_id`) REFERENCES `promotion_user` (`id`)
        ON DELETE RESTRICT
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='推广用户媒体账号';

-- 媒体账号在某个平台接入账号下的报备记录和持久任务状态。
CREATE TABLE `provider_media_filing`
(
    `id`                    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `connection_id`         BIGINT UNSIGNED NOT NULL COMMENT '平台接入账号ID',
    `media_account_id`      BIGINT UNSIGNED NOT NULL COMMENT '媒体账号ID',
    `status`                VARCHAR(16)     NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING审核中 APPROVED已加白 FAILED已失败',
    `submitted_data_version` INT                     DEFAULT NULL COMMENT '最近提交的资料版本',
    `remote_status`         VARCHAR(64)              DEFAULT NULL COMMENT '第三方原始状态',
    `external_filing_id`    VARCHAR(128)             DEFAULT NULL COMMENT '第三方报备ID',
    `filing_time`           DATETIME                 DEFAULT NULL COMMENT '第三方报备时间',
    `operate_time`          DATETIME                 DEFAULT NULL COMMENT '第三方审核时间',
    `next_action`           VARCHAR(16)     NOT NULL DEFAULT 'SUBMIT' COMMENT '任务动作：SUBMIT提交 QUERY查询 NONE无',
    `next_action_at`        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '下次任务时间',
    `retry_count`           INT             NOT NULL DEFAULT 0 COMMENT '连续重试次数',
    `last_submitted_at`     DATETIME                 DEFAULT NULL COMMENT '最近成功提交时间',
    `last_queried_at`       DATETIME                 DEFAULT NULL COMMENT '最近成功查询时间',
    `last_error_code`       VARCHAR(64)              DEFAULT NULL COMMENT '最近错误类型',
    `last_error_message`    VARCHAR(512)             DEFAULT NULL COMMENT '脱敏错误信息',
    `lease_owner`           VARCHAR(64)              DEFAULT NULL COMMENT '任务租约持有者',
    `lease_until`           DATETIME                 DEFAULT NULL COMMENT '任务租约到期时间',
    `created_at`            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_provider_media_filing` (`connection_id`, `media_account_id`),
    KEY `idx_filing_due_task` (`next_action`, `next_action_at`),
    KEY `idx_filing_media_account` (`media_account_id`),
    CONSTRAINT `fk_filing_connection`
        FOREIGN KEY (`connection_id`) REFERENCES `short_drama_connection` (`id`)
        ON DELETE RESTRICT,
    CONSTRAINT `fk_filing_media_account`
        FOREIGN KEY (`media_account_id`) REFERENCES `promotion_media_account` (`id`)
        ON DELETE RESTRICT
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='平台媒体账号报备';

-- =========================================================
-- Merged source: V3__provider_connection_base_url.sql
-- =========================================================
-- =========================================================
-- V3: 平台接入配置增加可管理的接口域名
-- =========================================================

ALTER TABLE `short_drama_connection`
    ADD COLUMN `base_url` VARCHAR(512) DEFAULT NULL
        COMMENT '平台接口域名（API报备必填）';

-- =========================================================
-- Merged source: V4__media_filing_task_version.sql
-- =========================================================
-- =========================================================
-- V4: 报备任务资料版本
-- =========================================================

ALTER TABLE `provider_media_filing`
    ADD COLUMN `task_data_version` INT NOT NULL DEFAULT 1
        COMMENT '当前异步任务对应的媒体账号资料版本'
        AFTER `submitted_data_version`;

-- =========================================================
-- Merged source: V5__provider_filing_mode.sql
-- =========================================================
ALTER TABLE `short_drama_connection`
    ADD COLUMN `filing_mode` VARCHAR(16) NOT NULL DEFAULT 'API'
        COMMENT 'Account filing mode: API or MANUAL';

-- =========================================================
-- Merged source: V6__manual_filing_operator.sql
-- =========================================================
-- =========================================================
-- V6: 手工报备操作人及无任务状态
-- =========================================================

ALTER TABLE `provider_media_filing`
    ADD COLUMN `operate_by` BIGINT UNSIGNED DEFAULT NULL COMMENT '人工处理管理员ID';

ALTER TABLE `provider_media_filing`
    MODIFY COLUMN `next_action_at` DATETIME DEFAULT NULL COMMENT '下次任务时间';

-- =========================================================
-- Merged source: V7__drama_catalog_sync.sql
-- =========================================================
-- GoodShort 短剧目录、剧集和同步检查点
CREATE TABLE `provider_drama`
(
    `id`                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `connection_id`       BIGINT UNSIGNED NOT NULL COMMENT '平台接入账号ID',
    `external_drama_id`   VARCHAR(128)    NOT NULL COMMENT '平台短剧ID',
    `title`               VARCHAR(255)    NOT NULL COMMENT '短剧标题',
    `original_title`      VARCHAR(255)             DEFAULT NULL COMMENT '原始标题',
    `description`         TEXT                     DEFAULT NULL COMMENT '短剧简介',
    `cover_url`           VARCHAR(1024)            DEFAULT NULL COMMENT '封面地址',
    `language`            VARCHAR(32)     NOT NULL COMMENT '语言',
    `drama_type`          VARCHAR(64)              DEFAULT NULL COMMENT '短剧类型',
    `remote_show_status`  VARCHAR(32)              DEFAULT NULL COMMENT '平台上下架状态',
    `local_status`        VARCHAR(16)     NOT NULL DEFAULT 'DRAFT' COMMENT '本地状态',
    `remote_updated_at`   DATETIME                 DEFAULT NULL COMMENT '平台更新时间',
    `last_seen_at`        DATETIME                 DEFAULT NULL COMMENT '最近同步发现时间',
    `created_at`          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_provider_drama_external` (`connection_id`, `external_drama_id`),
    KEY `idx_provider_drama_query` (`connection_id`, `language`, `local_status`),
    CONSTRAINT `fk_provider_drama_connection` FOREIGN KEY (`connection_id`) REFERENCES `short_drama_connection` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台短剧目录';

CREATE TABLE `provider_drama_content`
(
    `id`                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `drama_id`            BIGINT UNSIGNED NOT NULL COMMENT '短剧ID',
    `external_content_id` VARCHAR(128)             DEFAULT NULL COMMENT '平台剧集ID',
    `sequence_no`        INT             NOT NULL COMMENT '剧集序号',
    `title`               VARCHAR(255)             DEFAULT NULL COMMENT '剧集标题',
    `is_free`             TINYINT         NOT NULL DEFAULT 0 COMMENT '是否免费',
    `duration_seconds`    INT                      DEFAULT NULL COMMENT '时长（秒）',
    `remote_updated_at`   DATETIME                 DEFAULT NULL COMMENT '平台更新时间',
    `created_at`          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_provider_drama_content_sequence` (`drama_id`, `sequence_no`),
    CONSTRAINT `fk_provider_drama_content_drama` FOREIGN KEY (`drama_id`) REFERENCES `provider_drama` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台短剧剧集目录';

CREATE TABLE `provider_sync_checkpoint`
(
    `id`                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `connection_id`      BIGINT UNSIGNED NOT NULL COMMENT '平台接入账号ID',
    `sync_type`          VARCHAR(16)     NOT NULL COMMENT 'FULL/INCREMENTAL',
    `language`           VARCHAR(32)     NOT NULL COMMENT '同步语言',
    `status`             VARCHAR(16)     NOT NULL DEFAULT 'IDLE' COMMENT '同步状态',
    `page_no`            INT             NOT NULL DEFAULT 1 COMMENT '当前页码',
    `page_size`          INT             NOT NULL DEFAULT 100 COMMENT '分页大小',
    `update_time`        BIGINT                   DEFAULT NULL COMMENT '增量游标',
    `last_success_at`    DATETIME                 DEFAULT NULL COMMENT '最近成功时间',
    `requested_at`       DATETIME                 DEFAULT NULL COMMENT '请求时间',
    `started_at`         DATETIME                 DEFAULT NULL COMMENT '开始时间',
    `finished_at`        DATETIME                 DEFAULT NULL COMMENT '结束时间',
    `total_fetched`      INT             NOT NULL DEFAULT 0 COMMENT '拉取数量',
    `total_upserted`     INT             NOT NULL DEFAULT 0 COMMENT '写入数量',
    `inserted_count`     INT             NOT NULL DEFAULT 0 COMMENT '新增数量',
    `updated_count`      INT             NOT NULL DEFAULT 0 COMMENT '更新数量',
    `skipped_count`      INT             NOT NULL DEFAULT 0 COMMENT '跳过数量',
    `error_count`        INT             NOT NULL DEFAULT 0 COMMENT '异常数量',
    `last_error_code`    VARCHAR(64)              DEFAULT NULL COMMENT '错误码',
    `last_error_message` VARCHAR(512)             DEFAULT NULL COMMENT '错误信息',
    `lease_owner`        VARCHAR(64)              DEFAULT NULL COMMENT '租约持有者',
    `lease_until`        DATETIME                 DEFAULT NULL COMMENT '租约到期时间',
    `created_at`         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_provider_sync_checkpoint` (`connection_id`, `sync_type`, `language`),
    KEY `idx_provider_sync_due` (`status`, `requested_at`, `lease_until`),
    CONSTRAINT `fk_provider_sync_checkpoint_connection` FOREIGN KEY (`connection_id`) REFERENCES `short_drama_connection` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='短剧目录同步检查点';

-- =========================================================
-- Merged source: V8__provider_commission_rule.sql
-- =========================================================
-- 短剧平台默认分佣规则
CREATE TABLE `provider_commission_rule`
(
    `id`                         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `provider_id`                BIGINT UNSIGNED NOT NULL COMMENT '短剧平台ID',
    `channel_fee_rate`           DECIMAL(12, 10) NOT NULL COMMENT '渠道费率，0 到 1',
    `principal_fee_rate`         DECIMAL(12, 10) NOT NULL COMMENT '甲方手续费率，0 到 1',
    `principal_commission_rate`  DECIMAL(12, 10) NOT NULL COMMENT '甲方给我方分佣比例，0 到 1',
    `downstream_fee_rate`        DECIMAL(12, 10) NOT NULL COMMENT '我方手续费率，0 到 1',
    `downstream_commission_rate` DECIMAL(12, 10) NOT NULL COMMENT '我方给下游分佣比例，0 到 1',
    `created_by`                 BIGINT UNSIGNED NOT NULL COMMENT '创建管理员ID',
    `updated_by`                 BIGINT UNSIGNED NOT NULL COMMENT '最后修改管理员ID',
    `created_at`                 DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`                 DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_provider_commission_provider` (`provider_id`),
    CONSTRAINT `fk_provider_commission_provider`
        FOREIGN KEY (`provider_id`) REFERENCES `short_drama_provider` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='短剧平台默认分佣规则';

-- =========================================================
-- Merged source: V9__scheduled_task_config.sql
-- =========================================================
-- 系统固定定时任务配置
CREATE TABLE `system_scheduled_task`
(
    `id`               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `task_code`        VARCHAR(64)     NOT NULL COMMENT '固定任务编码',
    `title`            VARCHAR(128)    NOT NULL COMMENT '任务标题',
    `description`      VARCHAR(255)    NOT NULL COMMENT '任务说明',
    `interval_minutes` INT             NOT NULL COMMENT '执行间隔分钟数',
    `enabled`          TINYINT         NOT NULL DEFAULT 1 COMMENT '是否开启',
    `next_run_at`      DATETIME                 DEFAULT NULL COMMENT '下次入队时间',
    `lease_owner`      VARCHAR(64)              DEFAULT NULL COMMENT '租约持有者',
    `lease_until`      DATETIME                 DEFAULT NULL COMMENT '租约到期时间',
    `created_at`       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_system_scheduled_task_code` (`task_code`),
    KEY `idx_system_scheduled_task_due` (`enabled`, `next_run_at`, `lease_until`),
    CONSTRAINT `chk_system_scheduled_task_interval` CHECK (`interval_minutes` BETWEEN 5 AND 1440)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统固定定时任务配置';

INSERT INTO `system_scheduled_task`
    (`task_code`, `title`, `description`, `interval_minutes`, `enabled`, `next_run_at`)
VALUES
    ('GOODSHORT_DRAMA_INCREMENTAL_SYNC', 'GoodShort 短剧增量同步',
     '每隔60分钟执行一次GoodShort短剧目录增量同步', 60, 1,
     TIMESTAMPADD(MINUTE, 60, CURRENT_TIMESTAMP));

-- =========================================================
-- Merged source: V10__scheduled_task_cycle_config.sql
-- =========================================================
ALTER TABLE `system_scheduled_task` ADD COLUMN `cycle_type` VARCHAR(32) NOT NULL DEFAULT 'INTERVAL_MINUTES' COMMENT '周期类型';
ALTER TABLE `system_scheduled_task` ADD COLUMN `interval_value` INT DEFAULT NULL COMMENT '间隔值';
ALTER TABLE `system_scheduled_task` ADD COLUMN `interval_hours_part` INT DEFAULT 0 COMMENT '间隔小时余数';
ALTER TABLE `system_scheduled_task` ADD COLUMN `interval_minutes_part` INT DEFAULT 0 COMMENT '间隔分钟余数';
ALTER TABLE `system_scheduled_task` ADD COLUMN `time_of_day` TIME DEFAULT NULL COMMENT '执行时间';
ALTER TABLE `system_scheduled_task` ADD COLUMN `day_of_week` TINYINT DEFAULT NULL COMMENT '星期一至星期日';
ALTER TABLE `system_scheduled_task` ADD COLUMN `day_of_month` TINYINT DEFAULT NULL COMMENT '每月日期';
ALTER TABLE `system_scheduled_task` ADD COLUMN `month_of_year` TINYINT DEFAULT NULL COMMENT '每年月份';

UPDATE `system_scheduled_task`
SET `cycle_type` = 'INTERVAL_MINUTES', `interval_value` = `interval_minutes`
WHERE `cycle_type` IS NULL OR `interval_value` IS NULL;

-- 灏嗘椂闂寸増鏈敹鏁氫负姣忎釜骞冲彴涓€鏉″彲鐩存帴瑕嗙洊鐨勯粯璁ゅ垎浣ｈ鍒�
-- =========================================================
-- Merged source: V12__promotion_link.sql
-- =========================================================
CREATE TABLE `promotion_link` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT UNSIGNED NOT NULL COMMENT '推广用户内部 ID',
    `provider_id` BIGINT UNSIGNED NOT NULL COMMENT '短剧平台 ID',
    `connection_id` BIGINT UNSIGNED NOT NULL COMMENT '平台接入账号 ID',
    `drama_id` BIGINT UNSIGNED NOT NULL COMMENT '本地短剧 ID',
    `media_account_id` BIGINT UNSIGNED NOT NULL COMMENT '推广用户媒体账号 ID',
    `request_key` VARCHAR(64) NOT NULL COMMENT '用户请求幂等键',
    `tracking_no` VARCHAR(64) NOT NULL COMMENT '本地推广追踪号',
    `campaign_name` VARCHAR(128) DEFAULT NULL COMMENT '推广名称',
    `provider_code` VARCHAR(32) NOT NULL COMMENT '平台编码快照',
    `external_code` VARCHAR(255) DEFAULT NULL COMMENT '平台推广口令或编码',
    `share_url` VARCHAR(2048) DEFAULT NULL COMMENT '平台分享链接',
    `custom_params` VARCHAR(255) DEFAULT NULL COMMENT '平台回传自定义参数',
    `landing_type` VARCHAR(32) NOT NULL DEFAULT 'DEFAULT' COMMENT '落地页类型',
    `status` VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING生成中 SUCCESS成功 FAILED失败',
    `last_error_code` VARCHAR(64) DEFAULT NULL COMMENT '最近错误码',
    `last_error_message` VARCHAR(512) DEFAULT NULL COMMENT '最近错误摘要',
    `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_promotion_link_tracking_no` (`tracking_no`),
    UNIQUE KEY `uk_promotion_link_user_request` (`user_id`, `request_key`),
    KEY `idx_promotion_link_user_created` (`user_id`, `created_at`),
    CONSTRAINT `fk_promotion_link_user` FOREIGN KEY (`user_id`) REFERENCES `promotion_user` (`id`),
    CONSTRAINT `fk_promotion_link_provider` FOREIGN KEY (`provider_id`) REFERENCES `short_drama_provider` (`id`),
    CONSTRAINT `fk_promotion_link_connection` FOREIGN KEY (`connection_id`) REFERENCES `short_drama_connection` (`id`),
    CONSTRAINT `fk_promotion_link_drama` FOREIGN KEY (`drama_id`) REFERENCES `provider_drama` (`id`),
    CONSTRAINT `fk_promotion_link_media_account` FOREIGN KEY (`media_account_id`) REFERENCES `promotion_media_account` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='推广用户生成的推广链接';
