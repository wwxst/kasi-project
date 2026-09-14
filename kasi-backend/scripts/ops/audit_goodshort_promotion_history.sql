-- GoodShort 推广历史只读核查。先在服务器数据库执行并人工确认测试账号范围，再单独编写清理 SQL。
-- 本文件不修改数据。

-- A/B/E: promotion_link 总数、变体/状态分布、FAILED 数量。
SELECT COUNT(*) AS promotion_link_total FROM promotion_link;
SELECT link_variant, status, COUNT(*) AS record_count
FROM promotion_link
GROUP BY link_variant, status
ORDER BY link_variant, status;
SELECT COUNT(*) AS failed_promotion_link_total
FROM promotion_link
WHERE status = 'FAILED';

-- C: 同一甲方口令身份存在多个 externalCode。media_type 即发送给 GoodShort 的 codeMedia。
SELECT l.connection_id, c.connection_name, c.partner_id AS pid,
       l.drama_id, d.external_drama_id AS book_id,
       l.user_id, u.user_no AS custom_params, l.media_type AS code_media,
       COUNT(*) AS link_count,
       COUNT(DISTINCT l.external_code) AS external_code_count,
       GROUP_CONCAT(DISTINCT l.external_code ORDER BY l.external_code) AS external_codes,
       MIN(l.created_at) AS first_created_at, MAX(l.created_at) AS last_created_at
FROM promotion_link l
JOIN short_drama_connection c ON c.id = l.connection_id
JOIN provider_drama d ON d.id = l.drama_id
JOIN promotion_user u ON u.id = l.user_id
WHERE l.status = 'SUCCESS' AND l.external_code IS NOT NULL
GROUP BY l.connection_id, c.connection_name, c.partner_id,
         l.drama_id, d.external_drama_id, l.user_id, u.user_no, l.media_type
HAVING COUNT(DISTINCT l.external_code) > 1
ORDER BY last_created_at DESC;

-- D: 同一 externalCode 已同时存在 LANDING / ONELINK 的记录。
SELECT l.connection_id, c.connection_name, c.partner_id AS pid,
       l.drama_id, d.external_drama_id AS book_id,
       l.user_id, u.user_no AS custom_params, l.media_type AS code_media,
       l.external_code,
       COUNT(*) AS link_count,
       GROUP_CONCAT(DISTINCT l.link_variant ORDER BY l.link_variant) AS link_variants,
       GROUP_CONCAT(CONCAT(l.link_variant, '=', l.share_url) ORDER BY l.link_variant SEPARATOR '\n') AS variant_urls,
       MIN(l.created_at) AS first_created_at, MAX(l.created_at) AS last_created_at
FROM promotion_link l
JOIN short_drama_connection c ON c.id = l.connection_id
JOIN provider_drama d ON d.id = l.drama_id
JOIN promotion_user u ON u.id = l.user_id
WHERE l.status = 'SUCCESS' AND l.external_code IS NOT NULL
GROUP BY l.connection_id, c.connection_name, c.partner_id,
         l.drama_id, d.external_drama_id, l.user_id, u.user_no,
         l.media_type, l.external_code
HAVING COUNT(DISTINCT l.link_variant) = 2
ORDER BY last_created_at DESC;

-- V13 发布阻断项：以下结果非空时，新唯一索引会因同变体完全重复而创建失败。
SELECT l.connection_id, c.connection_name, c.partner_id AS pid,
       l.drama_id, d.external_drama_id AS book_id,
       l.user_id, u.user_no AS custom_params, l.media_type AS code_media,
       l.link_variant, l.external_code, COUNT(*) AS duplicate_count,
       GROUP_CONCAT(l.id ORDER BY l.id) AS promotion_link_ids,
       MIN(l.created_at) AS first_created_at, MAX(l.created_at) AS last_created_at
FROM promotion_link l
JOIN short_drama_connection c ON c.id = l.connection_id
JOIN provider_drama d ON d.id = l.drama_id
JOIN promotion_user u ON u.id = l.user_id
WHERE l.status = 'SUCCESS' AND l.external_code IS NOT NULL
GROUP BY l.connection_id, c.connection_name, c.partner_id,
         l.drama_id, d.external_drama_id, l.user_id, u.user_no,
         l.media_type, l.link_variant, l.external_code
HAVING COUNT(*) > 1
ORDER BY last_created_at DESC;

-- 测试账号归属核查：按 user_id/user_no/创建时间/连接查看所有推广记录。
SELECT l.id, l.created_at, l.updated_at, l.status, l.link_variant,
       l.media_type AS code_media, l.external_code, l.share_url,
       l.user_id, u.user_no, u.nickname, u.real_name,
       l.connection_id, c.connection_name, c.partner_id AS pid,
       l.drama_id, d.external_drama_id AS book_id,
       l.request_key, l.tracking_no, l.last_error_code, l.last_error_message
FROM promotion_link l
JOIN promotion_user u ON u.id = l.user_id
JOIN short_drama_connection c ON c.id = l.connection_id
JOIN provider_drama d ON d.id = l.drama_id
ORDER BY l.created_at DESC, l.id DESC;

-- F/G: 日报和订单总量。
SELECT COUNT(*) AS promotion_analytical_report_total,
       MIN(report_date) AS first_report_date, MAX(report_date) AS last_report_date
FROM promotion_analytical_report;
SELECT COUNT(*) AS promotion_order_total,
       MIN(created_at) AS first_created_at, MAX(created_at) AS last_created_at
FROM promotion_order;

-- 日报按 user_no(code 的 customParams)、PID、bookId 和 code 展开，便于与测试账号交叉确认。
SELECT r.custom_params AS user_no, r.pid, r.book_id, r.code,
       COUNT(*) AS report_days,
       SUM(r.click_count) AS click_count,
       SUM(r.order_count) AS order_count,
       SUM(r.order_amount) AS order_amount,
       MIN(r.report_date) AS first_report_date, MAX(r.report_date) AS last_report_date
FROM promotion_analytical_report r
GROUP BY r.custom_params, r.pid, r.book_id, r.code
ORDER BY last_report_date DESC;

-- 订单与推广链接、用户和佣金快照的逻辑关联。无物理外键，清理前必须逐项核对。
SELECT o.id, o.created_at, o.external_order_id, o.status, o.attribution_status,
       o.connection_id, o.partner_id AS pid, o.external_drama_id AS book_id,
       o.custom_params, o.search_code, o.promotion_link_id,
       o.user_id, u.user_no, o.tracking_no,
       o.order_amount, o.commission_status, o.commission_amount, o.rule_history_id
FROM promotion_order o
LEFT JOIN promotion_user u ON u.id = o.user_id
ORDER BY o.created_at DESC, o.id DESC;

-- H: 当前实现中的佣金相关业务数据量；正式账单/结算表若存在，也列出表名和行数供人工继续核查。
SELECT COUNT(*) AS commission_snapshot_order_total,
       SUM(commission_status = 'CALCULATED') AS calculated_total,
       SUM(commission_status = 'REVERSED') AS reversed_total,
       SUM(commission_status = 'ERROR') AS error_total,
       COALESCE(SUM(commission_amount), 0) AS commission_amount_total
FROM promotion_order
WHERE commission_status IS NOT NULL OR commission_amount IS NOT NULL;
SELECT COUNT(*) AS provider_commission_rule_total FROM provider_commission_rule;
SELECT COUNT(*) AS provider_commission_rule_history_total FROM provider_commission_rule_history;
SELECT table_name
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND (table_name LIKE '%settlement%' OR table_name LIKE '%bill%' OR table_name LIKE '%wallet%')
ORDER BY table_name;
