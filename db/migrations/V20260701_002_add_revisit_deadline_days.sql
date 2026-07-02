-- Adds a global "回访时限（天）" setting: how many natural days of grace a
-- "未处理" survey gets after its next_survey_date before it counts as overdue.
-- Additive only, idempotent (safe to re-run). Default 3 preserves the prior
-- effective behavior only if revisit_deadline_days is later treated as 0
-- elsewhere -- it is NOT; see SurveyController/TenantSystemSettingsService.
SET NAMES utf8mb4;

SET @add_column_sql := (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `tenant_system_settings` ADD COLUMN `revisit_deadline_days` int NOT NULL DEFAULT 3 AFTER `order_page_size`',
    'DO 0')
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'tenant_system_settings'
    AND column_name = 'revisit_deadline_days'
);
PREPARE add_column_stmt FROM @add_column_sql;
EXECUTE add_column_stmt;
DEALLOCATE PREPARE add_column_stmt;

-- DROP CHECK has no IF EXISTS in MySQL 8; guard via information_schema instead.
SET @drop_check_sql := (
  SELECT IF(COUNT(*) > 0,
    'ALTER TABLE `tenant_system_settings` DROP CHECK `chk_tenant_system_revisit_deadline_days`',
    'DO 0')
  FROM information_schema.table_constraints
  WHERE table_schema = DATABASE()
    AND table_name = 'tenant_system_settings'
    AND constraint_name = 'chk_tenant_system_revisit_deadline_days'
);
PREPARE drop_check_stmt FROM @drop_check_sql;
EXECUTE drop_check_stmt;
DEALLOCATE PREPARE drop_check_stmt;

ALTER TABLE `tenant_system_settings`
  ADD CONSTRAINT `chk_tenant_system_revisit_deadline_days`
  CHECK (`revisit_deadline_days` BETWEEN 1 AND 30);
