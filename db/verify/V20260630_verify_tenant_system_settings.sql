-- Verify tenant/global system settings schema and value distribution.
-- Does not select phone plaintext, ciphertext, IV, tag, or hashes.
SET NAMES utf8mb4;

SELECT TABLE_NAME
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'tenant_system_settings';

SELECT COLUMN_NAME
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'tenant_system_settings'
  AND COLUMN_NAME IN (
    'tenant_id',
    'phone_display_policy',
    'order_page_size',
    'enabled',
    'created_by',
    'updated_by',
    'created_at',
    'updated_at'
  )
ORDER BY COLUMN_NAME;

SELECT INDEX_NAME
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'tenant_system_settings'
  AND INDEX_NAME IN (
    'uk_tenant_system_settings',
    'idx_tenant_enabled'
  )
GROUP BY INDEX_NAME
ORDER BY INDEX_NAME;

SELECT
  SUM(`order_page_size` < 1 OR `order_page_size` > 100) AS invalid_order_page_size_rows,
  SUM(`phone_display_policy` NOT IN ('CLICK_TO_SESSION_VISIBLE', 'SINGLE_ORDER_TIMED_REVEAL', 'MASKED_ONLY')) AS invalid_phone_display_policy_rows
FROM `tenant_system_settings`;

SELECT
  `tenant_id`,
  `phone_display_policy`,
  `order_page_size`,
  COUNT(*) AS settings_count
FROM `tenant_system_settings`
WHERE `enabled` = 1
GROUP BY `tenant_id`, `phone_display_policy`, `order_page_size`
ORDER BY `tenant_id`, `phone_display_policy`, `order_page_size`;

SELECT
  SUM(`permission` = 'PRIVACY_POLICY_MANAGE') AS privacy_policy_manage_permissions
FROM `role_permission`;
