-- Fix-up migration: user_role consistency, sys_role Chinese re-seed, admin notice state restore.
-- Target: MySQL 8, utf8mb4 / utf8mb4_0900_ai_ci.
-- Apply with a utf8mb4 client connection, e.g.:
--   mysql --default-character-set=utf8mb4 xyyx_db < V20260626_003_fix_userrole_sysrole_notice.sql
-- Idempotent: safe to run more than once.
SET NAMES utf8mb4;

-- ---------------------------------------------------------------------------
-- P0-b: repair user_role
-- ---------------------------------------------------------------------------
-- 1) remove orphan user_role rows whose user no longer exists (e.g. user_id=2).
DELETE ur FROM `user_role` ur
LEFT JOIN `user` u ON u.`id` = ur.`user_id`
WHERE u.`id` IS NULL;

-- 2) create the missing active role link for any user without one
--    (maps legacy user.role admin/staff to sys_role.code ADMIN/STAFF), e.g. user 'user' (id=4).
INSERT INTO `user_role` (`user_id`, `role_id`, `assigned_by_user_id`, `created_at`, `revoked_at`)
SELECT u.`id`, r.`id`, NULL, CURRENT_TIMESTAMP, NULL
FROM `user` u
JOIN `sys_role` r
  ON r.`code` = CASE LOWER(u.`role`) WHEN 'admin' THEN 'ADMIN' WHEN 'staff' THEN 'STAFF' END
WHERE CASE LOWER(u.`role`) WHEN 'admin' THEN 'ADMIN' WHEN 'staff' THEN 'STAFF' END IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM `user_role` ur
    WHERE ur.`user_id` = u.`id` AND ur.`revoked_at` IS NULL
  );

-- ---------------------------------------------------------------------------
-- P1-a: overwrite the mojibake'd sys_role Chinese names/descriptions.
--   Existing rows were double-encoded (UTF-8 bytes read as GBK) and partly lossy,
--   so they cannot be reversed in place; overwrite with correct values instead.
--   `code` is ASCII and was never corrupted, so it is the safe key.
-- ---------------------------------------------------------------------------
UPDATE `sys_role` SET `name` = '管理员', `description` = '系统管理员'        WHERE `code` = 'ADMIN';
UPDATE `sys_role` SET `name` = '业务员', `description` = '一线销售/回访人员'  WHERE `code` = 'STAFF';
UPDATE `sys_role` SET `name` = '组长',   `description` = '销售小组负责人'      WHERE `code` = 'LEADER';
UPDATE `sys_role` SET `name` = '队长',   `description` = '销售队伍负责人'      WHERE `code` = 'CAPTAIN';
UPDATE `sys_role` SET `name` = '经理',   `description` = '销售管理人员'        WHERE `code` = 'MANAGER';

-- ---------------------------------------------------------------------------
-- P2: restore the two notices the admin (18007300157) had per-user soft-deleted.
--     is_read is left as-is (one was read, one unread).
-- ---------------------------------------------------------------------------
UPDATE `notice_user_state`
SET `is_deleted` = 0, `deleted_at` = NULL
WHERE `username` = '18007300157' AND `is_deleted` = 1;
