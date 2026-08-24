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
    KEY `idx_commission_rule_history_provider` (`provider_id`, `id`),
    CONSTRAINT `fk_commission_rule_history_provider`
        FOREIGN KEY (`provider_id`) REFERENCES `short_drama_provider` (`id`) ON DELETE RESTRICT,
    CONSTRAINT `fk_commission_rule_history_rule`
        FOREIGN KEY (`rule_id`) REFERENCES `provider_commission_rule` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='短剧平台分佣规则不可变历史快照';

INSERT INTO `provider_commission_rule_history`
    (`provider_id`, `rule_id`, `channel_fee_rate`, `principal_fee_rate`,
     `principal_commission_rate`, `downstream_fee_rate`, `downstream_commission_rate`,
     `created_by`, `created_at`)
SELECT `provider_id`, `id`, `channel_fee_rate`, `principal_fee_rate`,
       `principal_commission_rate`, `downstream_fee_rate`, `downstream_commission_rate`,
       `updated_by`, `updated_at`
FROM `provider_commission_rule`;

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
    `status`                     VARCHAR(16)     NOT NULL COMMENT 'UNPAID PAID REFUNDED UNKNOWN',
    `paid_at`                    DATETIME                 DEFAULT NULL COMMENT '支付时间',
    `provider_updated_at`        DATETIME                 DEFAULT NULL COMMENT '供应方更新时间',
    `custom_params`              VARCHAR(255)             DEFAULT NULL COMMENT '供应方原样返回的归因参数',
    `tracking_no`                VARCHAR(64)              DEFAULT NULL COMMENT '解析匹配的本地追踪号',
    `promotion_link_id`          BIGINT UNSIGNED          DEFAULT NULL COMMENT '推广链接ID',
    `user_id`                    BIGINT UNSIGNED          DEFAULT NULL COMMENT '推广用户ID',
    `media_account_id`           BIGINT UNSIGNED          DEFAULT NULL COMMENT '媒体账号ID',
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
    `first_synced_at`            DATETIME        NOT NULL COMMENT '首次同步时间',
    `last_synced_at`             DATETIME        NOT NULL COMMENT '最近同步时间',
    `last_error_message`         VARCHAR(512)             DEFAULT NULL,
    `created_at`                 DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`                 DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_promotion_order_source` (`connection_id`, `external_order_id`),
    KEY `idx_promotion_order_user_paid` (`user_id`, `paid_at`),
    KEY `idx_promotion_order_attribution` (`attribution_status`, `paid_at`),
    CONSTRAINT `fk_promotion_order_connection`
        FOREIGN KEY (`connection_id`) REFERENCES `short_drama_connection` (`id`) ON DELETE RESTRICT,
    CONSTRAINT `fk_promotion_order_provider`
        FOREIGN KEY (`provider_id`) REFERENCES `short_drama_provider` (`id`) ON DELETE RESTRICT,
    CONSTRAINT `fk_promotion_order_link`
        FOREIGN KEY (`promotion_link_id`) REFERENCES `promotion_link` (`id`) ON DELETE RESTRICT,
    CONSTRAINT `fk_promotion_order_user`
        FOREIGN KEY (`user_id`) REFERENCES `promotion_user` (`id`) ON DELETE RESTRICT,
    CONSTRAINT `fk_promotion_order_media_account`
        FOREIGN KEY (`media_account_id`) REFERENCES `promotion_media_account` (`id`) ON DELETE RESTRICT,
    CONSTRAINT `fk_promotion_order_drama`
        FOREIGN KEY (`drama_id`) REFERENCES `provider_drama` (`id`) ON DELETE RESTRICT,
    CONSTRAINT `fk_promotion_order_rule_history`
        FOREIGN KEY (`rule_history_id`) REFERENCES `provider_commission_rule_history` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='GoodShort短剧推广订单及佣金快照';
