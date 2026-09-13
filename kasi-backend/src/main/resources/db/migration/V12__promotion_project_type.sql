CREATE TABLE `promotion_project_type`
(
    `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `code`       VARCHAR(32)     NOT NULL COMMENT '类型编码',
    `name`       VARCHAR(64)     NOT NULL COMMENT '类型名称',
    `status`     VARCHAR(16)     NOT NULL DEFAULT 'ENABLED' COMMENT 'ENABLED/DISABLED',
    `sort_order` INT             NOT NULL DEFAULT 0 COMMENT '显示顺序，越小越靠前',
    `created_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_promotion_project_type_code` (`code`),
    KEY `idx_promotion_project_type_list` (`sort_order`, `id`),
    CONSTRAINT `ck_promotion_project_type_status` CHECK (`status` REGEXP '^(ENABLED|DISABLED)$'),
    CONSTRAINT `ck_promotion_project_type_sort_order` CHECK (`sort_order` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='推广项目类型';

INSERT INTO `promotion_project_type` (`code`, `name`, `status`, `sort_order`)
VALUES ('CPA', '按行动付费', 'ENABLED', 1),
       ('CPM', '按千次展示付费', 'ENABLED', 2),
       ('CPS', '按销售付费', 'ENABLED', 3);

ALTER TABLE `promotion_project`
    ADD COLUMN `project_type_id` BIGINT UNSIGNED NULL COMMENT '项目类型ID' AFTER `id`,
    ADD KEY `idx_promotion_project_type_id` (`project_type_id`);
