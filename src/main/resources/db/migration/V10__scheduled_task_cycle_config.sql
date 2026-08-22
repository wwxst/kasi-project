ALTER TABLE `system_scheduled_task` ADD COLUMN `cycle_type` VARCHAR(32) NOT NULL DEFAULT 'INTERVAL_MINUTES' COMMENT '周期类型';
ALTER TABLE `system_scheduled_task` ADD COLUMN `interval_value` INT DEFAULT NULL COMMENT '间隔值';
ALTER TABLE `system_scheduled_task` ADD COLUMN `time_of_day` TIME DEFAULT NULL COMMENT '执行时间';
ALTER TABLE `system_scheduled_task` ADD COLUMN `day_of_week` TINYINT DEFAULT NULL COMMENT '星期一至星期日';
ALTER TABLE `system_scheduled_task` ADD COLUMN `day_of_month` TINYINT DEFAULT NULL COMMENT '每月日期';
ALTER TABLE `system_scheduled_task` ADD COLUMN `month_of_year` TINYINT DEFAULT NULL COMMENT '每年月份';

UPDATE `system_scheduled_task`
SET `cycle_type` = 'INTERVAL_MINUTES', `interval_value` = `interval_minutes`
WHERE `cycle_type` IS NULL OR `interval_value` IS NULL;
