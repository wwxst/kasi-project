-- GoodShort 同一口令可对应 LANDING 和 ONELINK 两条本地变体记录。
-- 旧开发重建脚本曾额外创建四列唯一索引，生产 V1 并未包含该索引；按存在性删除以兼容两种历史结构。
SET @drop_promotion_link_identity_index = (
    SELECT IF(COUNT(*) > 0,
              'ALTER TABLE promotion_link DROP INDEX uk_promotion_link_external_identity',
              'SELECT 1')
    FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'promotion_link'
      AND INDEX_NAME = 'uk_promotion_link_external_identity'
);
PREPARE drop_promotion_link_identity_index_stmt FROM @drop_promotion_link_identity_index;
EXECUTE drop_promotion_link_identity_index_stmt;
DEALLOCATE PREPARE drop_promotion_link_identity_index_stmt;

ALTER TABLE promotion_link
    ADD UNIQUE KEY uk_promotion_link_external_identity
        (connection_id, drama_id, user_id, media_type, external_code, link_variant);
