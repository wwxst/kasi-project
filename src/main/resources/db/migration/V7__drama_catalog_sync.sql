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
    `external_content_id` VARCHAR(128)    NOT NULL COMMENT '平台剧集ID',
    `sequence_no`        INT             NOT NULL COMMENT '剧集序号',
    `title`               VARCHAR(255)             DEFAULT NULL COMMENT '剧集标题',
    `is_free`             TINYINT         NOT NULL DEFAULT 0 COMMENT '是否免费',
    `duration_seconds`    INT                      DEFAULT NULL COMMENT '时长（秒）',
    `remote_updated_at`   DATETIME                 DEFAULT NULL COMMENT '平台更新时间',
    `created_at`          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_provider_drama_content_sequence` (`drama_id`, `sequence_no`),
    UNIQUE KEY `uk_provider_drama_content_external` (`drama_id`, `external_content_id`),
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
    `update_time`        DATETIME                 DEFAULT NULL COMMENT '增量游标',
    `last_success_at`    DATETIME                 DEFAULT NULL COMMENT '最近成功时间',
    `requested_at`       DATETIME                 DEFAULT NULL COMMENT '请求时间',
    `started_at`         DATETIME                 DEFAULT NULL COMMENT '开始时间',
    `finished_at`        DATETIME                 DEFAULT NULL COMMENT '结束时间',
    `total_fetched`      INT             NOT NULL DEFAULT 0 COMMENT '拉取数量',
    `total_upserted`     INT             NOT NULL DEFAULT 0 COMMENT '写入数量',
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
