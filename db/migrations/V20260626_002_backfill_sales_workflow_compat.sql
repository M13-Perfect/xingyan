-- Repeatable data backfill for the first sales workflow compatibility step.
-- Safe to rerun: UPDATEs only fill NULLs, user_role INSERT excludes active duplicates.
-- Apply with a utf8mb4 client connection (mysql --default-character-set=utf8mb4).
SET NAMES utf8mb4;

UPDATE `survey` s
JOIN `user` u ON u.`username` = s.`owner`
SET s.`owner_user_id` = u.`id`
WHERE s.`owner_user_id` IS NULL
  AND s.`owner` IS NOT NULL
  AND s.`owner` <> '';

UPDATE `survey` s
JOIN `user` u ON u.`username` = s.`owner`
SET s.`created_by_user_id` = u.`id`
WHERE s.`created_by_user_id` IS NULL
  AND s.`owner` IS NOT NULL
  AND s.`owner` <> '';

UPDATE `notice_user_state` s
JOIN `user` u ON u.`username` = s.`username`
SET s.`user_id` = u.`id`
WHERE s.`user_id` IS NULL
  AND s.`username` IS NOT NULL
  AND s.`username` <> '';

INSERT INTO `user_role` (`user_id`, `role_id`, `assigned_by_user_id`, `created_at`, `revoked_at`)
SELECT u.`id`, r.`id`, NULL, CURRENT_TIMESTAMP, NULL
FROM `user` u
JOIN `sys_role` r
  ON (LOWER(u.`role`) = 'admin' AND r.`code` = 'ADMIN')
  OR (LOWER(u.`role`) = 'staff' AND r.`code` = 'STAFF')
WHERE LOWER(u.`role`) IN ('admin', 'staff')
  AND NOT EXISTS (
    SELECT 1
    FROM `user_role` existing
    WHERE existing.`user_id` = u.`id`
      AND existing.`role_id` = r.`id`
      AND existing.`revoked_at` IS NULL
  );
