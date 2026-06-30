-- Add the survey.social_account column that the backend (SurveyMapper insert/select)
-- requires but which was only ever present in the full bootstrap xyyx_db.sql, never in a
-- migration. Existing DBs upgraded via the V001/V002 migrations are missing it, so any
-- INSERT/SELECT on survey fails with "Unknown column 'social_account' in 'field list'".
-- Target: MySQL 8, utf8mb4 / utf8mb4_0900_ai_ci.
-- Idempotent: guarded by information_schema so it is safe to re-run.
SET NAMES utf8mb4;

SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'survey'
    AND COLUMN_NAME = 'social_account'
);

SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE `survey` ADD COLUMN `social_account` varchar(100) DEFAULT NULL AFTER `wechat`',
  'DO 0');

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
