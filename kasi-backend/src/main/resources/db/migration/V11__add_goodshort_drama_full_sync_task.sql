INSERT INTO system_scheduled_task
    (task_code, description, cycle_type, time_of_day, enabled, next_run_at)
SELECT 'GOODSHORT_DRAMA_FULL_SYNC',
       '每天 03:00 同步 GoodShort 全量短剧目录',
       'DAILY', '03:00:00', 1,
       CASE
           WHEN CURRENT_TIME < '03:00:00' THEN TIMESTAMPADD(HOUR, 3, CURRENT_DATE)
           ELSE TIMESTAMPADD(DAY, 1, TIMESTAMPADD(HOUR, 3, CURRENT_DATE))
       END
WHERE NOT EXISTS (
    SELECT 1 FROM system_scheduled_task
    WHERE task_code = 'GOODSHORT_DRAMA_FULL_SYNC'
);
