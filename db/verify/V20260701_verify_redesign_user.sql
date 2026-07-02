-- Verify user/user_role redesign schema and data integrity.
-- Does not select password hashes.
SET NAMES utf8mb4;

SELECT COLUMN_NAME
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'user'
  AND COLUMN_NAME IN (
    'tenant_id', 'status',
    'created_at', 'updated_at', 'is_deleted', 'deleted_at', 'created_by_user_id'
  )
ORDER BY COLUMN_NAME;

SELECT INDEX_NAME
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'user'
  AND INDEX_NAME = 'uk_user_tenant_username'
GROUP BY INDEX_NAME;

SELECT COLUMN_NAME
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'user_role'
  AND COLUMN_NAME = 'tenant_id';

-- Should be 0: no orphaned/invalid status, no post-migration username duplicates per tenant.
SELECT
  SUM(`status` NOT IN ('active', 'disabled')) AS invalid_status_rows,
  SUM(`tenant_id` IS NULL OR `tenant_id` = '') AS blank_tenant_rows
FROM `user`;

SELECT `tenant_id`, `username`, COUNT(*) AS dup_count
FROM `user`
GROUP BY `tenant_id`, `username`
HAVING COUNT(*) > 1;

SELECT COUNT(*) AS total_user_rows FROM `user`;
