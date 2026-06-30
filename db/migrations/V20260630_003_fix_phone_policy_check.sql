-- V20260630_003_fix_phone_policy_check.sql
-- Corrective migration. V20260630_001 created chk_tenant_system_phone_policy allowing only
-- ('CLICK_TO_SESSION_VISIBLE','MASKED_ONLY'); the OFF state of the "手机号隐私设置" toggle writes
-- 'SINGLE_ORDER_TIMED_REVEAL', which violated that CHECK and surfaced as a 500 on save.
-- Re-create the constraint to allow the new value. Additive/idempotent, no row mutation.
SET NAMES utf8mb4;

-- Drop the old constraint only if present (DROP CHECK has no IF EXISTS in MySQL 8).
SET @drop_sql := (
  SELECT IF(COUNT(*) > 0,
    'ALTER TABLE `tenant_system_settings` DROP CHECK `chk_tenant_system_phone_policy`',
    'DO 0')
  FROM information_schema.table_constraints
  WHERE table_schema = DATABASE()
    AND table_name = 'tenant_system_settings'
    AND constraint_name = 'chk_tenant_system_phone_policy'
);
PREPARE drop_stmt FROM @drop_sql;
EXECUTE drop_stmt;
DEALLOCATE PREPARE drop_stmt;

-- Re-add with the full set of valid policy values.
ALTER TABLE `tenant_system_settings`
  ADD CONSTRAINT `chk_tenant_system_phone_policy`
  CHECK (`phone_display_policy` IN ('CLICK_TO_SESSION_VISIBLE', 'SINGLE_ORDER_TIMED_REVEAL', 'MASKED_ONLY'));
