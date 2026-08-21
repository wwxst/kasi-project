-- 短剧平台分佣规则版本
CREATE TABLE `provider_commission_rule`
(
    `id`                         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `provider_id`                BIGINT UNSIGNED NOT NULL COMMENT '短剧平台ID',
    `channel_fee_rate`           DECIMAL(12, 10) NOT NULL COMMENT '渠道费率，0 到 1',
    `principal_fee_rate`         DECIMAL(12, 10) NOT NULL COMMENT '甲方手续费率，0 到 1',
    `principal_commission_rate`  DECIMAL(12, 10) NOT NULL COMMENT '甲方给我方分佣比例，0 到 1',
    `downstream_fee_rate`        DECIMAL(12, 10) NOT NULL COMMENT '我方手续费率，0 到 1',
    `downstream_commission_rate` DECIMAL(12, 10) NOT NULL COMMENT '我方给下游分佣比例，0 到 1',
    `effective_from`             DATETIME        NOT NULL COMMENT '开始生效时间',
    `effective_to`               DATETIME                 DEFAULT NULL COMMENT '结束时间，空表示长期有效',
    `created_by`                 BIGINT UNSIGNED NOT NULL COMMENT '创建管理员ID',
    `updated_by`                 BIGINT UNSIGNED NOT NULL COMMENT '最后修改管理员ID',
    `created_at`                 DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`                 DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_provider_commission_time` (`provider_id`, `effective_from`, `effective_to`),
    CONSTRAINT `fk_provider_commission_provider`
        FOREIGN KEY (`provider_id`) REFERENCES `short_drama_provider` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='短剧平台分佣规则版本';
