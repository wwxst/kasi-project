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
    `partner_id`         VARCHAR(64)     NOT NULL COMMENT '平台机构标识',
    `api_key_ciphertext` TEXT            NOT NULL COMMENT '平台密钥密文',
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
