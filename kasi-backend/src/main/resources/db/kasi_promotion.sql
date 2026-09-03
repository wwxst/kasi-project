-- Kasi Promotion 开发空库重建脚本
-- 始终表示当前最终结构，仅用于开发环境空库重建，不用于生产历史数据库升级。
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

-- 阿里云短信当前配置。AccessKey 只保存 AES-GCM 密文。
CREATE TABLE `system_sms_config`
(
    `id`                                BIGINT UNSIGNED NOT NULL COMMENT '固定单例配置ID',
    `access_key_id_ciphertext`          VARCHAR(1024)   NOT NULL COMMENT 'AccessKey ID AES-GCM密文',
    `access_key_secret_ciphertext`      VARCHAR(1024)   NOT NULL COMMENT 'AccessKey Secret AES-GCM密文',
    `sign_name`                         VARCHAR(64)     NOT NULL COMMENT '阿里云短信签名',
    `register_template_code`            VARCHAR(64)     NOT NULL COMMENT '注册验证码模板Code',
    `login_template_code`               VARCHAR(64)     NOT NULL COMMENT '验证码登录模板Code',
    `reset_password_template_code`      VARCHAR(64)     NOT NULL COMMENT '忘记密码模板Code',
    `smtp_host`                         VARCHAR(255)    DEFAULT NULL COMMENT 'SMTP服务器',
    `smtp_port`                         INT             DEFAULT NULL COMMENT 'SMTP端口',
    `smtp_username`                     VARCHAR(255)    DEFAULT NULL COMMENT 'SMTP账号',
    `smtp_password_ciphertext`          VARCHAR(1024)   DEFAULT NULL COMMENT 'SMTP密码AES-GCM密文',
    `smtp_from_address`                 VARCHAR(255)    DEFAULT NULL COMMENT '邮件发件地址',
    `email_enabled`                     TINYINT         NOT NULL DEFAULT 0 COMMENT '邮箱验证码开关',
    `enabled`                           TINYINT         NOT NULL DEFAULT 0 COMMENT '状态：0停用 1启用',
    `created_by`                        BIGINT UNSIGNED NOT NULL COMMENT '创建管理员逻辑关联ID',
    `updated_by`                        BIGINT UNSIGNED NOT NULL COMMENT '更新管理员逻辑关联ID',
    `created_at`                        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`                        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    CONSTRAINT `ck_system_sms_config_singleton` CHECK (`id` = 1),
    CONSTRAINT `ck_system_sms_config_enabled` CHECK (`enabled` >= 0 AND `enabled` <= 1)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='阿里云短信当前配置';

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
    `base_url`           VARCHAR(512)            DEFAULT NULL COMMENT '平台接口域名（API报备必填）',
    `media_root_domain`  VARCHAR(253)            DEFAULT NULL COMMENT '媒体资源允许根域（API报备必填）',
    `currency`           CHAR(3)         NOT NULL COMMENT 'ISO 4217币种',
    `filing_mode`        VARCHAR(16)     NOT NULL DEFAULT 'API' COMMENT 'Account filing mode: API or MANUAL',
    `status`             TINYINT         NOT NULL DEFAULT 1 COMMENT '状态：0禁用 1启用',
    `created_by`         BIGINT UNSIGNED          DEFAULT NULL COMMENT '创建管理员',
    `updated_by`         BIGINT UNSIGNED          DEFAULT NULL COMMENT '更新管理员',
    `created_at`         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_drama_connection_provider` (`provider_id`)) ENGINE = InnoDB
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
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_media_account_platform_id` (`media_type`, `external_account_id`),
    KEY `idx_media_account_user_status` (`user_id`, `status`)) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='推广用户媒体账号';

-- 媒体账号在某个平台接入账号下的报备记录和持久任务状态。
CREATE TABLE `provider_media_filing`
(
    `id`                    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `connection_id`         BIGINT UNSIGNED NOT NULL COMMENT '平台接入账号ID',
    `media_account_id`      BIGINT UNSIGNED NOT NULL COMMENT '媒体账号ID',
    `status`                VARCHAR(16)     NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING审核中 APPROVED已加白 FAILED已失败',
    `submitted_data_version` INT                     DEFAULT NULL COMMENT '最近提交的资料版本',
    `task_data_version`      INT             NOT NULL DEFAULT 1 COMMENT '当前异步任务对应的媒体账号资料版本',
    `remote_status`         VARCHAR(64)              DEFAULT NULL COMMENT '第三方原始状态',
    `external_filing_id`    VARCHAR(128)             DEFAULT NULL COMMENT '第三方报备ID',
    `filing_time`           DATETIME                 DEFAULT NULL COMMENT '第三方报备时间',
    `operate_time`          DATETIME                 DEFAULT NULL COMMENT '第三方审核时间',
    `next_action`           VARCHAR(16)     NOT NULL DEFAULT 'SUBMIT' COMMENT '任务动作：SUBMIT提交 QUERY查询 NONE无',
    `next_action_at`        DATETIME                 DEFAULT NULL COMMENT '下次任务时间',
    `retry_count`           INT             NOT NULL DEFAULT 0 COMMENT '连续重试次数',
    `last_submitted_at`     DATETIME                 DEFAULT NULL COMMENT '最近成功提交时间',
    `last_queried_at`       DATETIME                 DEFAULT NULL COMMENT '最近成功查询时间',
    `last_error_code`       VARCHAR(64)              DEFAULT NULL COMMENT '最近错误类型',
    `last_error_message`    VARCHAR(512)             DEFAULT NULL COMMENT '脱敏错误信息',
    `operate_by`            BIGINT UNSIGNED          DEFAULT NULL COMMENT '人工处理管理员ID',
    `lease_owner`           VARCHAR(64)              DEFAULT NULL COMMENT '任务租约持有者',
    `lease_until`           DATETIME                 DEFAULT NULL COMMENT '任务租约到期时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_provider_media_filing` (`connection_id`, `media_account_id`),
    KEY `idx_filing_due_task` (`next_action`, `next_action_at`),
    KEY `idx_filing_media_account` (`media_account_id`)) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='平台媒体账号报备';

-- GoodShort 短剧目录、剧集和同步检查点
CREATE TABLE `provider_drama`
(
    `id`                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `connection_id`       BIGINT UNSIGNED NOT NULL COMMENT '平台接入账号ID',
    `external_drama_id`   VARCHAR(128)    NOT NULL COMMENT '平台短剧ID',
    `title`               VARCHAR(255)    NOT NULL COMMENT '短剧标题',
    `title_zh`            VARCHAR(255)             DEFAULT NULL COMMENT '短剧中文名',
    `original_title`      VARCHAR(255)             DEFAULT NULL COMMENT '原始标题',
    `description`         TEXT                     DEFAULT NULL COMMENT '短剧简介',
    `cover_url`           VARCHAR(1024)            DEFAULT NULL COMMENT '封面地址',
    `label_names`         TEXT                     DEFAULT NULL COMMENT '短剧标签 JSON 数组',
    `category_name`       VARCHAR(255)             DEFAULT NULL COMMENT '短剧分类名',
    `language`            VARCHAR(32)     NOT NULL COMMENT '语言',
    `drama_type`          VARCHAR(64)              DEFAULT NULL COMMENT '短剧类型',
    `remote_rank`         INT                      DEFAULT NULL COMMENT '平台排序值',
    `novel_type`          VARCHAR(32)              DEFAULT NULL COMMENT '短剧类型 ORIGINAL/TRANSLATION',
    `novel_sub_type`      INT                      DEFAULT NULL COMMENT '短剧子类型 0 字幕 1 配音',
    `commission_scope`    VARCHAR(255)             DEFAULT NULL COMMENT '推广分佣范围编码，逗号分隔',
    `promotion_description` TEXT                   DEFAULT NULL COMMENT '推广说明',
    `remote_show_status`  VARCHAR(32)              DEFAULT NULL COMMENT '平台上下架状态',
    `local_status`        VARCHAR(16)     NOT NULL DEFAULT 'PUBLISHED' COMMENT '本地状态',
    `remote_created_at`   DATETIME                 DEFAULT NULL COMMENT '平台创建时间',
    `remote_updated_at`   DATETIME                 DEFAULT NULL COMMENT '平台更新时间',
    `last_seen_at`        DATETIME                 DEFAULT NULL COMMENT '最近同步发现时间',
    `created_at`          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_provider_drama_external` (`connection_id`, `external_drama_id`),
    KEY `idx_provider_drama_query` (`connection_id`, `language`, `local_status`)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台短剧目录';

CREATE TABLE `provider_drama_content`
(
    `id`                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `drama_id`            BIGINT UNSIGNED NOT NULL COMMENT '短剧ID',
    `external_content_id` VARCHAR(128)             DEFAULT NULL COMMENT '平台剧集ID',
    `sequence_no`        INT             NOT NULL COMMENT '剧集序号',
    `title`               VARCHAR(255)             DEFAULT NULL COMMENT '剧集标题',
    `is_free`             TINYINT         NOT NULL DEFAULT 0 COMMENT '是否免费',
    `duration_seconds`    INT                      DEFAULT NULL COMMENT '时长（秒）',
    `content_url`         TEXT                     DEFAULT NULL COMMENT 'GoodShort免费视频地址',
    `remote_updated_at`   DATETIME                 DEFAULT NULL COMMENT '平台更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_provider_drama_content_sequence` (`drama_id`, `sequence_no`)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台短剧剧集目录';

CREATE TABLE `provider_drama_content_sync_task`
(
    `id`                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `drama_id`           BIGINT UNSIGNED NOT NULL COMMENT '短剧ID',
    `status`             VARCHAR(16)     NOT NULL DEFAULT 'REQUESTED' COMMENT 'REQUESTED/RUNNING/SUCCESS/FAILED',
    `requested_at`       DATETIME        NOT NULL COMMENT '最近请求时间',
    `next_run_at`        DATETIME        NOT NULL COMMENT '下次执行时间',
    `retry_count`        INT             NOT NULL DEFAULT 0 COMMENT '连续重试次数',
    `total_fetched`      INT             NOT NULL DEFAULT 0 COMMENT '获取剧集数',
    `inserted_count`     INT             NOT NULL DEFAULT 0 COMMENT '新增剧集数',
    `updated_count`      INT             NOT NULL DEFAULT 0 COMMENT '更新剧集数',
    `last_error_code`    VARCHAR(64)              DEFAULT NULL COMMENT '最后错误码',
    `last_error_message` VARCHAR(512)             DEFAULT NULL COMMENT '最后错误信息',
    `lease_owner`        VARCHAR(64)              DEFAULT NULL COMMENT '租约持有者',
    `lease_until`        DATETIME                 DEFAULT NULL COMMENT '租约到期时间',
    `created_at`         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_drama_content_sync_task_drama` (`drama_id`),
    KEY `idx_drama_content_sync_task_due` (`status`, `next_run_at`, `lease_until`)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='短剧免费剧集同步任务';

CREATE TABLE `drama_sync_display_run`
(
    `id`             VARCHAR(36)  NOT NULL COMMENT '展示运行UUID',
    `provider_id`    BIGINT UNSIGNED NOT NULL COMMENT '短剧平台ID',
    `parent_run_id`  VARCHAR(36)           DEFAULT NULL COMMENT '父目录展示运行UUID',
    `sync_domain`    VARCHAR(16)  NOT NULL COMMENT 'CATALOG/CONTENT',
    `task_type`      VARCHAR(32) NOT NULL COMMENT '展示任务类型',
    `trigger_source` VARCHAR(16) NOT NULL COMMENT 'MANUAL/SCHEDULED',
    `requested_at`   DATETIME     NOT NULL COMMENT '本次触发时间',
    `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_drama_sync_display_run_query` (`provider_id`, `sync_domain`, `requested_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='同步记录展示运行';

CREATE TABLE `drama_sync_display_run_item`
(
    `run_id`      VARCHAR(36) NOT NULL COMMENT '展示运行UUID',
    `task_domain` VARCHAR(16) NOT NULL COMMENT 'CATALOG/CONTENT',
    `task_id`     BIGINT UNSIGNED NOT NULL COMMENT '现有同步任务ID',
    PRIMARY KEY (`run_id`, `task_domain`, `task_id`),
    KEY `idx_drama_sync_display_run_item_task` (`task_domain`, `task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='同步记录展示子任务关联';

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
    `total_fetched`      INT             NOT NULL DEFAULT 0 COMMENT '拉取数量',
    `inserted_count`     INT             NOT NULL DEFAULT 0 COMMENT '新增数量',
    `updated_count`      INT             NOT NULL DEFAULT 0 COMMENT '更新数量',
    `error_count`        INT             NOT NULL DEFAULT 0 COMMENT '异常数量',
    `last_error_code`    VARCHAR(64)              DEFAULT NULL COMMENT '错误码',
    `last_error_message` VARCHAR(512)             DEFAULT NULL COMMENT '错误信息',
    `lease_owner`        VARCHAR(64)              DEFAULT NULL COMMENT '租约持有者',
    `lease_until`        DATETIME                 DEFAULT NULL COMMENT '租约到期时间',
    `created_at`         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_provider_sync_checkpoint` (`connection_id`, `sync_type`, `language`),
    KEY `idx_provider_sync_due` (`status`, `requested_at`, `lease_until`)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='短剧目录同步检查点';

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
    UNIQUE KEY `uk_provider_commission_provider` (`provider_id`)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='短剧平台默认分佣规则';

-- 系统固定定时任务配置
CREATE TABLE system_scheduled_task (
    task_code VARCHAR(64) NOT NULL PRIMARY KEY,
    description VARCHAR(255) NOT NULL,
    cycle_type VARCHAR(32) NOT NULL DEFAULT 'INTERVAL_MINUTES',
    interval_value INT DEFAULT NULL,
    interval_hours_part INT DEFAULT 0,
    interval_minutes_part INT DEFAULT 0,
    time_of_day TIME DEFAULT NULL,
    day_of_week TINYINT DEFAULT NULL,
    day_of_month TINYINT DEFAULT NULL,
    month_of_year TINYINT DEFAULT NULL,
    enabled TINYINT NOT NULL DEFAULT 1,
    next_run_at DATETIME DEFAULT NULL,
    lease_owner VARCHAR(64) DEFAULT NULL,
    lease_until DATETIME DEFAULT NULL,
    KEY idx_system_scheduled_task_due (enabled, next_run_at, lease_until)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO system_scheduled_task (task_code, description, cycle_type, interval_value, interval_hours_part, interval_minutes_part, enabled, next_run_at) VALUES ('GOODSHORT_DRAMA_INCREMENTAL_SYNC','每隔60分钟执行一次GoodShort短剧目录增量同步','INTERVAL_MINUTES',60,0,0,1,TIMESTAMPADD(MINUTE,60,CURRENT_TIMESTAMP)),('GOODSHORT_DRAMA_CONTENT_SYNC','每隔1分钟同步GoodShort免费剧集','INTERVAL_MINUTES',1,0,0,1,TIMESTAMPADD(MINUTE,1,CURRENT_TIMESTAMP)),('GOODSHORT_ORDER_SYNC','每隔1分钟同步最近3天的GoodShort订单','INTERVAL_MINUTES',1,0,0,1,TIMESTAMPADD(MINUTE,1,CURRENT_TIMESTAMP));
-- 推广链接
CREATE TABLE `promotion_link` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT UNSIGNED NOT NULL COMMENT '推广用户内部 ID',
    `provider_id` BIGINT UNSIGNED NOT NULL COMMENT '短剧平台 ID',
    `connection_id` BIGINT UNSIGNED NOT NULL COMMENT '平台接入账号 ID',
    `drama_id` BIGINT UNSIGNED NOT NULL COMMENT '本地短剧 ID',
    `batch_no` VARCHAR(64) NOT NULL COMMENT '同一批次编号',
    `media_type` VARCHAR(32) NOT NULL COMMENT '媒体平台编码',
    `link_variant` VARCHAR(16) NOT NULL COMMENT '链接变体 LANDING/ONELINK',
    `request_key` VARCHAR(64) NOT NULL COMMENT '用户请求幂等键',
    `tracking_no` VARCHAR(64) NOT NULL COMMENT '本地推广追踪号',
    `campaign_name` VARCHAR(128) DEFAULT NULL COMMENT '推广名称',
    `external_code` VARCHAR(255) DEFAULT NULL COMMENT '平台推广口令或编码',
    `share_url` VARCHAR(2048) DEFAULT NULL COMMENT '平台分享链接',
    `status` VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING生成中 SUCCESS成功 FAILED失败',
    `last_error_code` VARCHAR(64) DEFAULT NULL COMMENT '最近错误码',
    `last_error_message` VARCHAR(512) DEFAULT NULL COMMENT '最近错误摘要',
    `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_promotion_link_tracking_no` (`tracking_no`),
    UNIQUE KEY `uk_promotion_link_variant` (`user_id`, `request_key`, `media_type`, `link_variant`),
    KEY `idx_promotion_link_user_created` (`user_id`, `created_at`),
    KEY `idx_promotion_link_batch` (`user_id`, `batch_no`)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='推广用户生成的推广链接';

-- 短剧平台分佣规则不可变历史快照
CREATE TABLE `provider_commission_rule_history`
(
    `id`                         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '历史快照ID',
    `provider_id`                BIGINT UNSIGNED NOT NULL COMMENT '短剧平台ID',
    `rule_id`                    BIGINT UNSIGNED NOT NULL COMMENT '当前规则记录ID',
    `channel_fee_rate`           DECIMAL(12, 10) NOT NULL COMMENT '渠道费率，0到1',
    `principal_fee_rate`         DECIMAL(12, 10) NOT NULL COMMENT '甲方手续费率，0到1',
    `principal_commission_rate`  DECIMAL(12, 10) NOT NULL COMMENT '甲方给我方分佣比例，0到1',
    `downstream_fee_rate`        DECIMAL(12, 10) NOT NULL COMMENT '我方手续费率，0到1',
    `downstream_commission_rate` DECIMAL(12, 10) NOT NULL COMMENT '我方给下游分佣比例，0到1',
    `created_by`                 BIGINT UNSIGNED NOT NULL COMMENT '产生快照的管理员ID',
    `created_at`                 DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_commission_rule_history_provider` (`provider_id`, `id`)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='短剧平台分佣规则不可变历史快照';

-- GoodShort 短剧推广订单及佣金快照
CREATE TABLE `promotion_order`
(
    `id`                         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '订单ID',
    `connection_id`              BIGINT UNSIGNED NOT NULL COMMENT '平台接入账号ID',
    `provider_id`                BIGINT UNSIGNED NOT NULL COMMENT '短剧平台ID',
    `external_order_id`          VARCHAR(128)    NOT NULL COMMENT '供应方订单ID',
    `external_user_id`           VARCHAR(128)             DEFAULT NULL COMMENT '供应方下单用户ID',
    `external_drama_id`          VARCHAR(128)             DEFAULT NULL COMMENT '供应方短剧ID',
    `search_code`                VARCHAR(64)              DEFAULT NULL COMMENT 'GoodShort搜索口令',
    `channel_code`               VARCHAR(128)             DEFAULT NULL COMMENT 'GoodShort渠道号',
    `partner_id`                 VARCHAR(128)             DEFAULT NULL COMMENT '供应方机构ID快照',
    `order_amount_minor`         BIGINT UNSIGNED NOT NULL COMMENT '供应方金额最小单位，美分',
    `order_amount`               DECIMAL(18, 2)  NOT NULL COMMENT '分成前订单金额',
    `currency`                   VARCHAR(16)     NOT NULL COMMENT '连接币种快照',
    `raw_status`                 VARCHAR(64)     NOT NULL COMMENT '供应方原始支付状态',
    `status`                     VARCHAR(16)     NOT NULL COMMENT 'UNPAID未支付 PAID已支付 REFUNDED已退款 UNKNOWN未知',
    `paid_at`                    DATETIME                 DEFAULT NULL COMMENT '支付时间',
    `provider_updated_at`        DATETIME                 DEFAULT NULL COMMENT '供应方更新时间',
    `custom_params`              VARCHAR(255)             DEFAULT NULL COMMENT '供应方原样返回的归因参数',
    `tracking_no`                VARCHAR(64)              DEFAULT NULL COMMENT '解析匹配的本地追踪号',
    `promotion_link_id`          BIGINT UNSIGNED          DEFAULT NULL COMMENT '推广链接ID',
    `user_id`                    BIGINT UNSIGNED          DEFAULT NULL COMMENT '推广用户ID',
    `drama_id`                   BIGINT UNSIGNED          DEFAULT NULL COMMENT '本地短剧ID',
    `attribution_status`         VARCHAR(16)     NOT NULL DEFAULT 'UNATTRIBUTED' COMMENT 'ATTRIBUTED UNATTRIBUTED',
    `rule_history_id`            BIGINT UNSIGNED          DEFAULT NULL COMMENT '分佣规则历史快照ID',
    `channel_fee_rate`           DECIMAL(12, 10)          DEFAULT NULL,
    `principal_fee_rate`         DECIMAL(12, 10)          DEFAULT NULL,
    `principal_commission_rate`  DECIMAL(12, 10)          DEFAULT NULL,
    `downstream_fee_rate`        DECIMAL(12, 10)          DEFAULT NULL,
    `downstream_commission_rate` DECIMAL(12, 10)          DEFAULT NULL,
    `commission_amount`          DECIMAL(18, 2)           DEFAULT NULL COMMENT '用户佣金快照',
    `commission_status`          VARCHAR(24)              DEFAULT NULL COMMENT 'CALCULATED REVERSED NOT_APPLICABLE ERROR',
    `raw_payload_json`           LONGTEXT        NOT NULL COMMENT '供应方单条订单原始JSON',
    `sync_start_date`            DATETIME        NOT NULL COMMENT '本次同步窗口开始',
    `sync_end_date`              DATETIME        NOT NULL COMMENT '本次同步窗口结束',
    `last_synced_at`             DATETIME        NOT NULL COMMENT '最近同步时间',
    `last_error_message`         VARCHAR(512)             DEFAULT NULL,
    `created_at`                 DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`                 DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_promotion_order_source` (`connection_id`, `external_order_id`),
    KEY `idx_promotion_order_user_paid` (`user_id`, `paid_at`),
    KEY `idx_promotion_order_attribution` (`attribution_status`, `paid_at`),
    CONSTRAINT `ck_promotion_order_status` CHECK (`status` REGEXP '^(UNPAID|PAID|REFUNDED|UNKNOWN)$')) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='GoodShort短剧推广订单及佣金快照';
