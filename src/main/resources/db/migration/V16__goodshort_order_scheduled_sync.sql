INSERT INTO `system_scheduled_task`
    (`task_code`, `title`, `description`, `cycle_type`, `interval_value`,
     `interval_hours_part`, `interval_minutes_part`, `interval_minutes`,
     `enabled`, `next_run_at`)
VALUES
    ('GOODSHORT_ORDER_SYNC', 'GoodShort订单同步',
     '每隔1分钟同步最近3天的GoodShort订单', 'INTERVAL_MINUTES', 1,
     0, 0, 5, 1, TIMESTAMPADD(MINUTE, 1, CURRENT_TIMESTAMP));
