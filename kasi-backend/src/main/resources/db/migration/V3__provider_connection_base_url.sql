-- =========================================================
-- V3: 平台接入配置增加可管理的接口域名
-- =========================================================

ALTER TABLE `short_drama_connection`
    ADD COLUMN `base_url` VARCHAR(512) NOT NULL DEFAULT 'https://api.novelopen.com/creek'
        COMMENT '平台接口域名';
