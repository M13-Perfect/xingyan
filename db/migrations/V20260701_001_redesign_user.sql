-- Redesign `user` for multi-tenant, non-restrictive account identifiers.
-- Target: MySQL 8, utf8mb4 / utf8mb4_0900_ai_ci.
-- Additive only: no DROP/RENAME of existing columns or data. `role` is kept
-- (deprecated) for backward compatibility with code paths not yet switched
-- to `user_role` + `sys_role`. Run once, after backup.
SET NAMES utf8mb4;

-- Nickname/avatar are intentionally NOT columns here: they live in Casdoor
-- (displayName/avatar) as the single source of truth; the personal-center
-- feature reads/writes them via Casdoor's account API instead of a local copy.
ALTER TABLE `user`
  ADD COLUMN `tenant_id` varchar(64) NOT NULL DEFAULT 'default' AFTER `id`,
  ADD COLUMN `status` varchar(20) NOT NULL DEFAULT 'active' AFTER `role`,
  ADD COLUMN `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP AFTER `status`,
  ADD COLUMN `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP AFTER `created_at`,
  ADD COLUMN `is_deleted` tinyint(1) NOT NULL DEFAULT 0 AFTER `updated_at`,
  ADD COLUMN `deleted_at` datetime NULL AFTER `is_deleted`,
  ADD COLUMN `created_by_user_id` int NULL AFTER `deleted_at`,
  ADD CONSTRAINT `ck_user_status` CHECK (`status` IN ('active', 'disabled'));

-- Existing rows already satisfy uniqueness under (tenant_id, username): they were
-- globally unique under the old uk_user_username, and all get tenant_id='default'.
ALTER TABLE `user` DROP INDEX `uk_user_username`;
ALTER TABLE `user` ADD UNIQUE KEY `uk_user_tenant_username` (`tenant_id`, `username`);

ALTER TABLE `user_role`
  ADD COLUMN `tenant_id` varchar(64) NOT NULL DEFAULT 'default' AFTER `user_id`,
  ADD KEY `idx_user_role_tenant` (`tenant_id`, `revoked_at`);
