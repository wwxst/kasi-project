-- Provider commission rule versions shared by all dramas on a provider.
CREATE TABLE `provider_commission_rule`
(
    `id`                         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    `provider_id`                BIGINT UNSIGNED NOT NULL COMMENT 'Drama provider ID',
    `channel_fee_rate`           DECIMAL(12, 10) NOT NULL COMMENT 'Channel fee rate',
    `principal_fee_rate`         DECIMAL(12, 10) NOT NULL COMMENT 'Principal fee rate',
    `principal_commission_rate`  DECIMAL(12, 10) NOT NULL COMMENT 'Principal commission rate',
    `downstream_fee_rate`        DECIMAL(12, 10) NOT NULL COMMENT 'Downstream fee rate',
    `downstream_commission_rate` DECIMAL(12, 10) NOT NULL COMMENT 'Downstream commission rate',
    `effective_from`             DATETIME        NOT NULL COMMENT 'Effective start time',
    `effective_to`               DATETIME                 DEFAULT NULL COMMENT 'Effective end time; NULL means no end',
    `created_by`                 BIGINT UNSIGNED NOT NULL COMMENT 'Creating admin ID',
    `updated_by`                 BIGINT UNSIGNED NOT NULL COMMENT 'Last updating admin ID',
    `created_at`                 DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`                 DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_provider_commission_time` (`provider_id`, `effective_from`, `effective_to`),
    CONSTRAINT `fk_provider_commission_provider`
        FOREIGN KEY (`provider_id`) REFERENCES `short_drama_provider` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Provider commission rule versions';
