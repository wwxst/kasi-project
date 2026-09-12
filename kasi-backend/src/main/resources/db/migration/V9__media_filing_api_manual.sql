ALTER TABLE short_drama_connection
    ADD COLUMN api_filing_media_types VARCHAR(256) NOT NULL DEFAULT '["FACEBOOK"]'
        COMMENT '使用API报白的媒体类型JSON数组';

ALTER TABLE provider_media_filing
    ADD COLUMN filing_method VARCHAR(16) NOT NULL DEFAULT 'API' AFTER media_account_id;
ALTER TABLE provider_media_filing
    ADD COLUMN last_submit_attempt_at DATETIME NULL AFTER retry_count;
ALTER TABLE provider_media_filing
    ADD COLUMN manual_updated_by BIGINT UNSIGNED NULL AFTER last_queried_at;
ALTER TABLE provider_media_filing
    ADD COLUMN manual_updated_at DATETIME NULL AFTER manual_updated_by;

UPDATE provider_media_filing
SET filing_method = CASE WHEN (
        SELECT media_type
        FROM promotion_media_account
        WHERE id = provider_media_filing.media_account_id
    ) = 'FACEBOOK' THEN 'API' ELSE 'MANUAL' END,
    status = CASE
        WHEN remote_status = '1' OR status = 'APPROVED' THEN 'APPROVED'
        WHEN remote_status = '2' THEN 'REJECTED'
        WHEN last_submitted_at IS NOT NULL THEN 'PENDING'
        WHEN NULLIF(TRIM(last_error_message), '') IS NOT NULL THEN 'SUBMIT_FAILED'
        ELSE 'NOT_SUBMITTED'
    END;

UPDATE provider_media_filing
SET next_action = CASE
        WHEN filing_method = 'MANUAL' THEN 'NONE'
        WHEN status = 'NOT_SUBMITTED' THEN 'SUBMIT'
        WHEN status = 'PENDING' THEN 'QUERY'
        ELSE 'NONE'
    END,
    next_action_at = CASE
        WHEN filing_method = 'API' AND status IN ('NOT_SUBMITTED', 'PENDING')
            THEN COALESCE(next_action_at, CURRENT_TIMESTAMP)
        ELSE NULL
    END,
    lease_owner = NULL,
    lease_until = NULL,
    retry_count = 0;

ALTER TABLE provider_media_filing
    MODIFY COLUMN status VARCHAR(16) NOT NULL DEFAULT 'NOT_SUBMITTED';
