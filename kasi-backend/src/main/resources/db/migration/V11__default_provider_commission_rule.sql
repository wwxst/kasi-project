-- 灏嗘椂闂寸増鏈敹鏁氫负姣忎釜骞冲彴涓€鏉″彲鐩存帴瑕嗙洊鐨勯粯璁ゅ垎浣ｈ鍒�
CREATE TABLE `provider_commission_rule_keep`
AS
SELECT id
FROM (
    SELECT id,
           ROW_NUMBER() OVER (
               PARTITION BY provider_id
               ORDER BY CASE
                            WHEN effective_from <= CURRENT_TIMESTAMP
                                 AND (effective_to IS NULL OR effective_to > CURRENT_TIMESTAMP)
                            THEN 0 ELSE 1
                        END,
                        effective_from DESC,
                        id DESC
           ) AS row_no
    FROM `provider_commission_rule`
) ranked
WHERE row_no = 1;

DELETE FROM `provider_commission_rule`
WHERE id NOT IN (SELECT id FROM `provider_commission_rule_keep`);

DROP TABLE `provider_commission_rule_keep`;

ALTER TABLE `provider_commission_rule`
    DROP INDEX `idx_provider_commission_time`;

ALTER TABLE `provider_commission_rule`
    DROP COLUMN `effective_from`;

ALTER TABLE `provider_commission_rule`
    DROP COLUMN `effective_to`;

ALTER TABLE `provider_commission_rule`
    ADD UNIQUE KEY `uk_provider_commission_provider` (`provider_id`);
