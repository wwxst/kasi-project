UPDATE system_scheduled_task
SET description = '每天 08:00 同步 GoodShort 推广转化日报'
WHERE task_code = 'GOODSHORT_ANALYTICAL_REPORT_SYNC'
  AND description = 'Daily 08:00 GoodShort analytical report sync';
