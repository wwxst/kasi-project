CREATE TABLE promotion_analytical_report (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '推广转化日报ID',
    report_date DATE NOT NULL COMMENT '日报自然日',
    pid VARCHAR(128) NOT NULL COMMENT 'GoodShort平台PID',
    custom_params VARCHAR(255) NOT NULL DEFAULT '' COMMENT 'GoodShort customParams，对应推广用户user_no',
    book_id VARCHAR(128) NOT NULL DEFAULT '' COMMENT 'GoodShort短剧bookId',
    code VARCHAR(128) NOT NULL DEFAULT '' COMMENT 'GoodShort口令',
    click_count BIGINT UNSIGNED NOT NULL,
    attributed_user_count BIGINT UNSIGNED NOT NULL,
    new_registered_user_count BIGINT UNSIGNED NOT NULL,
    new_paid_user_count BIGINT UNSIGNED NOT NULL,
    new_member_user_count BIGINT UNSIGNED NOT NULL,
    paid_user_count BIGINT UNSIGNED NOT NULL,
    order_count BIGINT UNSIGNED NOT NULL,
    order_amount DECIMAL(18,2) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_promotion_analytical_report_dimension (report_date, pid, custom_params, book_id, code),
    KEY idx_promotion_analytical_report_date (report_date),
    KEY idx_promotion_analytical_report_custom_params (custom_params),
    KEY idx_promotion_analytical_report_book (book_id),
    KEY idx_promotion_analytical_report_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='GoodShort每日推广转化汇总';

INSERT INTO system_scheduled_task
    (task_code, description, cycle_type, time_of_day, enabled, next_run_at)
VALUES
    ('GOODSHORT_ANALYTICAL_REPORT_SYNC', 'Daily 08:00 GoodShort analytical report sync', 'DAILY', '08:00:00', 1,
     TIMESTAMPADD(HOUR, 8, CURRENT_DATE));
