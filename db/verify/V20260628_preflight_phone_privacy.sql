-- Preflight check before V001. Safe when phone privacy columns are missing.
-- Does not select plaintext phone values.
SET NAMES utf8mb4;

SELECT expected.column_name, IF(actual.COLUMN_NAME IS NULL, 'MISSING', 'PRESENT') AS status
FROM (
  SELECT 'tenant_id' AS column_name, 1 AS sort_order UNION ALL
  SELECT 'customer_uuid', 2 UNION ALL
  SELECT 'phone_ciphertext', 3 UNION ALL
  SELECT 'phone_iv', 4 UNION ALL
  SELECT 'phone_tag', 5 UNION ALL
  SELECT 'phone_enc_key_version', 6 UNION ALL
  SELECT 'phone_hash', 7 UNION ALL
  SELECT 'phone_hash_key_version', 8 UNION ALL
  SELECT 'phone_mask', 9 UNION ALL
  SELECT 'phone_suffix4_hash', 10 UNION ALL
  SELECT 'phone_region', 11 UNION ALL
  SELECT 'phone_normalized_version', 12
) expected
LEFT JOIN information_schema.COLUMNS actual
  ON actual.TABLE_SCHEMA = DATABASE()
  AND actual.TABLE_NAME = 'survey'
  AND actual.COLUMN_NAME = expected.column_name
ORDER BY expected.sort_order;

SELECT
  COUNT(*) AS total_rows,
  SUM(`phone` IS NOT NULL) AS legacy_phone_rows
FROM `survey`;
