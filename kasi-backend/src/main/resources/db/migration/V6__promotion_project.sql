CREATE TABLE `promotion_project`
(
    `id`                   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `name`                 VARCHAR(128)    NOT NULL COMMENT '项目名称',
    `cover_image_url`      VARCHAR(512)    NOT NULL COMMENT '封面访问路径',
    `project_document_url` VARCHAR(1024)   NOT NULL COMMENT '项目文档HTTPS地址',
    `status`               VARCHAR(16)     NOT NULL DEFAULT 'ENABLED' COMMENT 'ENABLED/DISABLED',
    `sort_order`           INT             NOT NULL DEFAULT 0 COMMENT '显示顺序，越小越靠前',
    `created_at`           DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`           DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_promotion_project_user_list` (`status`, `sort_order`, `id`),
    CONSTRAINT `ck_promotion_project_status` CHECK (`status` REGEXP '^(ENABLED|DISABLED)$'),
    CONSTRAINT `ck_promotion_project_sort_order` CHECK (`sort_order` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='推广项目';
