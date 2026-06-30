-- Verify phone privacy schema and backfill coverage without selecting plaintext phone values.
SET NAMES utf8mb4;

SELECT COLUMN_NAME
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'survey'
  AND COLUMN_NAME IN (
    'tenant_id',
    'customer_uuid',
    'phone_ciphertext',
    'phone_iv',
    'phone_tag',
    'phone_enc_key_version',
    'phone_hash',
    'phone_hash_key_version',
    'phone_mask',
    'phone_suffix4_hash',
    'phone_region',
    'phone_normalized_version'
  )
ORDER BY FIELD(
  COLUMN_NAME,
  'tenant_id',
  'customer_uuid',
  'phone_ciphertext',
  'phone_iv',
  'phone_tag',
  'phone_enc_key_version',
  'phone_hash',
  'phone_hash_key_version',
  'phone_mask',
  'phone_suffix4_hash',
  'phone_region',
  'phone_normalized_version'
);

SELECT
  COUNT(*) AS total_rows,
  SUM(`phone` IS NOT NULL AND `phone` <> '') AS legacy_phone_rows,
  SUM(`phone_hash` IS NOT NULL) AS hashed_rows,
  SUM(`phone_suffix4_hash` IS NOT NULL) AS suffix_hash_rows,
  SUM(`phone_mask` IS NOT NULL AND `phone_mask` <> '') AS masked_rows,
  SUM(`phone_ciphertext` IS NOT NULL AND `phone_iv` IS NOT NULL AND `phone_tag` IS NOT NULL) AS encrypted_rows,
  SUM(`phone` IS NOT NULL AND `phone` <> '' AND (
    `phone_hash` IS NULL
    OR `phone_suffix4_hash` IS NULL
    OR `phone_mask` IS NULL
    OR `phone_mask` = ''
    OR `phone_ciphertext` IS NULL
    OR `phone_iv` IS NULL
    OR `phone_tag` IS NULL
  )) AS need_backfill_rows
FROM `survey`;
