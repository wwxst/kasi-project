-- =========================================================
-- V4: 报备任务资料版本
-- =========================================================

ALTER TABLE `provider_media_filing`
    ADD COLUMN `task_data_version` INT NOT NULL DEFAULT 1
        COMMENT '当前异步任务对应的媒体账号资料版本'
        AFTER `submitted_data_version`;
