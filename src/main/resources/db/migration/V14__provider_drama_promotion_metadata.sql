ALTER TABLE `provider_drama`
    ADD COLUMN `commission_scope` VARCHAR(255) DEFAULT NULL COMMENT '推广分佣范围编码，逗号分隔';

ALTER TABLE `provider_drama`
    ADD COLUMN `promotion_description` TEXT DEFAULT NULL COMMENT '推广说明';
