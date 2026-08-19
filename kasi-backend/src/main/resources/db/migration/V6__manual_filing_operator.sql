-- =========================================================
-- V6: 手工报备操作人及无任务状态
-- =========================================================

ALTER TABLE `provider_media_filing`
    ADD COLUMN `operate_by` BIGINT UNSIGNED DEFAULT NULL COMMENT '人工处理管理员ID';

ALTER TABLE `provider_media_filing`
    MODIFY COLUMN `next_action_at` DATETIME DEFAULT NULL COMMENT '下次任务时间';
