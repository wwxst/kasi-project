ALTER TABLE `provider_drama` ADD COLUMN `title_zh` VARCHAR(255) DEFAULT NULL COMMENT '短剧中文名';
ALTER TABLE `provider_drama` ADD COLUMN `label_names` TEXT DEFAULT NULL COMMENT '短剧标签 JSON 数组';
ALTER TABLE `provider_drama` ADD COLUMN `category_name` VARCHAR(255) DEFAULT NULL COMMENT '短剧分类名';
ALTER TABLE `provider_drama` ADD COLUMN `remote_rank` INT DEFAULT NULL COMMENT '平台排序值';
ALTER TABLE `provider_drama` ADD COLUMN `novel_type` VARCHAR(32) DEFAULT NULL COMMENT '短剧类型 ORIGINAL/TRANSLATION';
ALTER TABLE `provider_drama` ADD COLUMN `novel_sub_type` INT DEFAULT NULL COMMENT '短剧子类型 0 字幕 1 配音';
ALTER TABLE `provider_drama` ADD COLUMN `remote_created_at` DATETIME DEFAULT NULL COMMENT '平台创建时间';
