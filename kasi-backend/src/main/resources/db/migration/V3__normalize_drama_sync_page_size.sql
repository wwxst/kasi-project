ALTER TABLE provider_sync_checkpoint
    MODIFY COLUMN page_size INT NOT NULL DEFAULT 50 COMMENT '分页大小';

UPDATE provider_sync_checkpoint
SET page_size = 50,
    page_no = CASE WHEN status IN ('REQUESTED', 'RUNNING', 'FAILED') THEN 1 ELSE page_no END,
    total_fetched = CASE WHEN status IN ('REQUESTED', 'RUNNING', 'FAILED') THEN 0 ELSE total_fetched END,
    inserted_count = CASE WHEN status IN ('REQUESTED', 'RUNNING', 'FAILED') THEN 0 ELSE inserted_count END,
    updated_count = CASE WHEN status IN ('REQUESTED', 'RUNNING', 'FAILED') THEN 0 ELSE updated_count END,
    error_count = CASE WHEN status IN ('REQUESTED', 'RUNNING', 'FAILED') THEN 0 ELSE error_count END
WHERE page_size > 50;
