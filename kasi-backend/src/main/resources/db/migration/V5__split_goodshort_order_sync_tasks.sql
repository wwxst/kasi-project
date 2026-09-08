-- GoodShort 订单同步拆分：实时今日、小时级昨日+今日、低频七日补偿。
-- 仅更新任务配置，不修改订单数据。

UPDATE system_scheduled_task
SET description = '每隔60分钟同步昨天和今天的GoodShort订单',
    cycle_type = 'INTERVAL_MINUTES',
    interval_value = 60,
    interval_hours_part = 0,
    interval_minutes_part = 0,
    enabled = 1,
    next_run_at = TIMESTAMPADD(MINUTE, 60, CURRENT_TIMESTAMP)
WHERE task_code = 'GOODSHORT_ORDER_SYNC';

INSERT INTO system_scheduled_task
    (task_code, description, cycle_type, interval_value, interval_hours_part, interval_minutes_part, enabled, next_run_at)
VALUES
    ('GOODSHORT_ORDER_TODAY_SYNC', '每隔5分钟同步今天的GoodShort订单', 'INTERVAL_MINUTES', 5, 0, 0, 1,
     TIMESTAMPADD(MINUTE, 5, CURRENT_TIMESTAMP)),
    ('GOODSHORT_ORDER_RECENT_SYNC', '每隔3天补偿同步最近7天的GoodShort订单', 'INTERVAL_DAYS', 3, 0, 0, 1,
     TIMESTAMPADD(DAY, 3, CURRENT_TIMESTAMP))
ON DUPLICATE KEY UPDATE
    description = VALUES(description),
    cycle_type = VALUES(cycle_type),
    interval_value = VALUES(interval_value),
    interval_hours_part = VALUES(interval_hours_part),
    interval_minutes_part = VALUES(interval_minutes_part),
    enabled = VALUES(enabled),
    next_run_at = VALUES(next_run_at);
