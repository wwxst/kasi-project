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
