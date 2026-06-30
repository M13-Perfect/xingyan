-- Verify phone reveal session schema, permissions, audit fields, and privacy coverage.
-- Does not select plaintext phone values.
SET NAMES utf8mb4;

SELECT TABLE_NAME
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME IN (
    'privacy_phone_reveal_policy',
    'phone_reveal_login_session',
    'personal_info_access_log'
  )
ORDER BY TABLE_NAME;

SELECT COLUMN_NAME
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'privacy_phone_reveal_policy'
  AND COLUMN_NAME IN (
    'tenant_id',
    'reveal_visibility_mode',
    'auto_hide_seconds',
    'extended_duration_seconds',
    'allow_page_session_revealed_only',
    'allow_login_session_show_all',
    'login_session_show_all_max_seconds',
    'login_session_show_all_requires_permission',
    'reveal_rate_limit_seconds',
    'per_user_minute_limit',
    'per_ip_minute_limit',
    'session_batch_decrypt_per_user_minute_limit',
    'session_batch_decrypt_per_ip_minute_limit',
    'session_batch_max_page_size',
    'created_by',
    'updated_by',
    'created_at',
    'updated_at'
  )
ORDER BY COLUMN_NAME;

SELECT COLUMN_NAME
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'phone_reveal_login_session'
  AND COLUMN_NAME IN (
    'tenant_id',
    'user_id',
    'login_session_id_hash',
    'enabled',
    'enabled_at',
    'expires_at',
    'disabled_at',
    'disabled_reason',
    'policy_version',
    'ip_address',
    'user_agent',
    'created_at',
    'updated_at'
  )
ORDER BY COLUMN_NAME;

SELECT COLUMN_NAME
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'personal_info_access_log'
  AND COLUMN_NAME IN (
    'operator_user_id',
    'login_session_id_hash',
    'target_type',
    'target_ids_json',
    'record_count',
    'query_hash',
    'page',
    'page_size',
    'reveal_visibility_mode',
    'request_id'
  )
ORDER BY COLUMN_NAME;

SELECT INDEX_NAME
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME IN (
    'privacy_phone_reveal_policy',
    'phone_reveal_login_session',
    'personal_info_access_log'
  )
  AND INDEX_NAME IN (
    'uk_tenant_phone_reveal_policy',
    'uk_tenant_user_login_session',
    'idx_expires_at',
    'idx_user_enabled',
    'idx_pial_request_id',
    'idx_pial_login_session'
  )
GROUP BY INDEX_NAME
ORDER BY INDEX_NAME;

SELECT
  SUM(`permission` = 'PHONE_VIEW_FULL_SESSION') AS phone_view_full_session_permissions,
  SUM(`permission` = 'PRIVACY_POLICY_MANAGE') AS privacy_policy_manage_permissions
FROM `role_permission`;

SELECT
  COUNT(*) AS default_policy_rows,
  SUM(`allow_login_session_show_all` = 0) AS default_session_show_all_disabled_rows,
  MIN(`session_batch_max_page_size`) AS min_session_batch_max_page_size
FROM `privacy_phone_reveal_policy`
WHERE `tenant_id` = 'default';

SELECT
  COUNT(*) AS total_rows,
  SUM(`phone_hash` IS NOT NULL) AS hashed_rows,
  SUM(`phone_mask` IS NOT NULL AND `phone_mask` <> '') AS masked_rows,
  SUM(`phone_ciphertext` IS NOT NULL AND `phone_iv` IS NOT NULL AND `phone_tag` IS NOT NULL) AS encrypted_rows,
  SUM(
    `phone` IS NOT NULL
    AND `phone` <> ''
    AND (
      `phone_hash` IS NULL
      OR `phone_mask` IS NULL
      OR `phone_mask` = ''
      OR `phone_ciphertext` IS NULL
      OR `phone_iv` IS NULL
      OR `phone_tag` IS NULL
      OR `phone_suffix4_hash` IS NULL
    )
  ) AS need_backfill_rows
FROM `survey`;
