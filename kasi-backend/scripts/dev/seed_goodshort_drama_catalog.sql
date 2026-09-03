-- LOCAL DEVELOPMENT ONLY: deterministic GoodShort catalog fixtures.
-- This script is never invoked by the runtime application.

BEGIN;

CREATE TEMPORARY TABLE seed_goodshort_numbers (
    n INT NOT NULL PRIMARY KEY
);

INSERT INTO seed_goodshort_numbers (n) VALUES
    (1),(2),(3),(4),(5),(6),(7),(8),(9),(10),(11),(12),
    (13),(14),(15),(16),(17),(18),(19),(20),(21),(22),(23),(24);

CREATE TEMPORARY TABLE seed_goodshort_episode_numbers (
    n INT NOT NULL PRIMARY KEY
);

INSERT INTO seed_goodshort_episode_numbers (n) VALUES
    (1),(2),(3),(4),(5),(6),(7),(8),(9),(10),(11),(12);

CREATE TEMPORARY TABLE seed_goodshort_guard (
    guard_value INT NOT NULL PRIMARY KEY
);

INSERT INTO seed_goodshort_guard (guard_value) VALUES (1);

-- A missing provider or a real connection deliberately causes a duplicate-key failure.
INSERT INTO seed_goodshort_guard (guard_value)
SELECT 1
WHERE NOT EXISTS (
    SELECT 1 FROM short_drama_provider WHERE provider_code = 'GOODSHORT'
);

INSERT INTO seed_goodshort_guard (guard_value)
SELECT 1
FROM short_drama_connection c
JOIN short_drama_provider p ON p.id = c.provider_id
WHERE p.provider_code = 'GOODSHORT'
  AND NOT (
      c.connection_name = 'GoodShort local fixture'
      AND c.currency = 'USD'
      AND c.status = 0
      AND c.filing_mode = 'MANUAL'
      AND c.partner_id IS NULL
      AND c.api_key_ciphertext IS NULL
  AND c.base_url IS NULL
      AND c.media_root_domain IS NULL
  );

SET @goodshort_provider_id = (
    SELECT id FROM short_drama_provider WHERE provider_code = 'GOODSHORT'
);

INSERT INTO short_drama_connection (
    provider_id, connection_name, currency, status, filing_mode,
    partner_id, api_key_ciphertext, base_url, media_root_domain
)
SELECT @goodshort_provider_id, 'GoodShort local fixture', 'USD', 0, 'MANUAL',
       NULL, NULL, NULL, NULL
WHERE @goodshort_provider_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM short_drama_connection WHERE provider_id = @goodshort_provider_id
  );

SET @goodshort_connection_id = (
    SELECT id FROM short_drama_connection
    WHERE provider_id = @goodshort_provider_id
      AND connection_name = 'GoodShort local fixture'
);

INSERT INTO provider_drama (
    connection_id, external_drama_id, title, original_title, description,
    cover_url, language, drama_type, remote_show_status, local_status,
    remote_created_at, remote_updated_at, last_seen_at
)
SELECT
    @goodshort_connection_id,
    CONCAT('990000', LPAD(n, 2, '0')),
    CASE WHEN n <= 12 THEN CONCAT('GoodShort English Story ', n)
         ELSE CONCAT('GoodShort Spanish Story ', n) END,
    CONCAT('Local GoodShort Drama ', n),
    CONCAT('Deterministic local development description for drama ', n),
    CASE WHEN MOD(n, 6) = 0 THEN NULL
         ELSE CONCAT('https://placehold.co/300x450/png?text=GoodShort+', LPAD(n, 2, '0')) END,
    CASE WHEN n <= 12 THEN 'ENGLISH' ELSE 'SPANISH' END,
    CASE MOD(n, 4)
        WHEN 0 THEN 'ROMANCE'
        WHEN 1 THEN 'FAMILY'
        WHEN 2 THEN 'REVENGE'
        ELSE 'COMEDY'
    END,
    CASE WHEN MOD(n, 3) = 0 THEN 'OFFLINE' ELSE 'ONLINE' END,
    CASE MOD(n, 3)
        WHEN 0 THEN 'DRAFT'
        WHEN 1 THEN 'PUBLISHED'
        ELSE 'OFFLINE'
    END,
    '2026-01-01 00:00:00',
    '2026-01-02 00:00:00',
    '2026-01-02 00:00:00'
FROM seed_goodshort_numbers
WHERE @goodshort_connection_id IS NOT NULL
ON DUPLICATE KEY UPDATE
    title = VALUES(title),
    original_title = VALUES(original_title),
    description = VALUES(description),
    cover_url = VALUES(cover_url),
    language = VALUES(language),
    drama_type = VALUES(drama_type),
    remote_show_status = VALUES(remote_show_status),
    local_status = VALUES(local_status),
    remote_created_at = VALUES(remote_created_at),
    remote_updated_at = VALUES(remote_updated_at),
    last_seen_at = VALUES(last_seen_at);

INSERT INTO provider_drama_content (
    drama_id, external_content_id, sequence_no, title, is_free,
    duration_seconds, remote_updated_at
)
SELECT
    d.id,
    CONCAT(d.external_drama_id, LPAD(n.n, 3, '0')),
    n.n,
    CONCAT(d.title, ' Episode ', n.n),
    CASE WHEN n.n <= 2 THEN 1 ELSE 0 END,
    300 + (n.n * 15),
    '2026-01-02 00:00:00'
FROM provider_drama d
JOIN seed_goodshort_numbers drama_number
  ON d.external_drama_id = CONCAT('990000', LPAD(drama_number.n, 2, '0'))
JOIN seed_goodshort_episode_numbers n
  ON n.n <= 5 + MOD(drama_number.n - 1, 8)
WHERE d.connection_id = @goodshort_connection_id
  AND @goodshort_connection_id IS NOT NULL
ON DUPLICATE KEY UPDATE
    external_content_id = VALUES(external_content_id),
    title = VALUES(title),
    is_free = VALUES(is_free),
    duration_seconds = VALUES(duration_seconds),
    remote_updated_at = VALUES(remote_updated_at);

INSERT INTO provider_sync_checkpoint (
    connection_id, sync_type, language, status, page_no, page_size,
    update_time, last_success_at, requested_at, total_fetched,
    inserted_count, updated_count, error_count, last_error_code,
    last_error_message, lease_owner, lease_until
)
SELECT @goodshort_connection_id, sync_type, language, 'SUCCESS', 1, 100,
       CASE WHEN sync_type = 'FULL' THEN 100 ELSE 200 END,
       '2026-01-03 00:00:00', '2026-01-03 00:00:00', 12,
       CASE WHEN sync_type = 'FULL' THEN 12
            WHEN language = 'ENGLISH' THEN 2 ELSE 1 END,
       CASE WHEN sync_type = 'FULL' THEN 0
            WHEN language = 'ENGLISH' THEN 9 ELSE 10 END,
       0, NULL, NULL, NULL, NULL
FROM (SELECT 'FULL' AS sync_type UNION ALL SELECT 'INCREMENTAL') sync_types
JOIN (SELECT 'ENGLISH' AS language UNION ALL SELECT 'SPANISH') languages
ON 1 = 1
WHERE @goodshort_connection_id IS NOT NULL
ON DUPLICATE KEY UPDATE
    status = VALUES(status),
    page_no = VALUES(page_no),
    page_size = VALUES(page_size),
    update_time = VALUES(update_time),
    last_success_at = VALUES(last_success_at),
    requested_at = VALUES(requested_at),
    total_fetched = VALUES(total_fetched),
    inserted_count = VALUES(inserted_count),
    updated_count = VALUES(updated_count),
    error_count = VALUES(error_count),
    last_error_code = VALUES(last_error_code),
    last_error_message = VALUES(last_error_message),
    lease_owner = VALUES(lease_owner),
    lease_until = VALUES(lease_until);

/*!50000 DROP TEMPORARY TABLE seed_goodshort_guard */;
/*!50000 DROP TEMPORARY TABLE seed_goodshort_numbers */;
/*!50000 DROP TEMPORARY TABLE seed_goodshort_episode_numbers */;

COMMIT;
