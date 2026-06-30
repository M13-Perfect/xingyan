-- Phone privacy fields for the active customer table (`survey`).
-- Target: MySQL 8, utf8mb4 / utf8mb4_0900_ai_ci.
-- Idempotent and additive: does not drop legacy survey.phone; application code stops
-- reading/writing it so data can be encrypted/backfilled before later cleanup.
SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS add_survey_phone_privacy_column;
DELIMITER //
CREATE PROCEDURE add_survey_phone_privacy_column(
  IN p_column_name varchar(64),
  IN p_column_ddl text
)
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'survey'
      AND COLUMN_NAME = p_column_name
  ) THEN
    SET @ddl = CONCAT('ALTER TABLE `survey` ADD COLUMN ', p_column_ddl);
    PREPARE stmt FROM @ddl;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
  END IF;
END//
DELIMITER ;

CALL add_survey_phone_privacy_column('tenant_id', '`tenant_id` varchar(64) NOT NULL DEFAULT ''default'' AFTER `id`');
CALL add_survey_phone_privacy_column('customer_uuid', '`customer_uuid` char(36) DEFAULT NULL AFTER `tenant_id`');
CALL add_survey_phone_privacy_column('phone_ciphertext', '`phone_ciphertext` varbinary(255) DEFAULT NULL AFTER `phone`');
CALL add_survey_phone_privacy_column('phone_iv', '`phone_iv` binary(12) DEFAULT NULL AFTER `phone_ciphertext`');
CALL add_survey_phone_privacy_column('phone_tag', '`phone_tag` binary(16) DEFAULT NULL AFTER `phone_iv`');
CALL add_survey_phone_privacy_column('phone_enc_key_version', '`phone_enc_key_version` varchar(32) DEFAULT NULL AFTER `phone_tag`');
CALL add_survey_phone_privacy_column('phone_hash', '`phone_hash` binary(32) DEFAULT NULL AFTER `phone_enc_key_version`');
CALL add_survey_phone_privacy_column('phone_hash_key_version', '`phone_hash_key_version` varchar(32) DEFAULT NULL AFTER `phone_hash`');
CALL add_survey_phone_privacy_column('phone_mask', '`phone_mask` varchar(32) DEFAULT NULL AFTER `phone_hash_key_version`');
CALL add_survey_phone_privacy_column('phone_suffix4_hash', '`phone_suffix4_hash` binary(32) DEFAULT NULL AFTER `phone_mask`');
CALL add_survey_phone_privacy_column('phone_region', '`phone_region` varchar(8) DEFAULT NULL AFTER `phone_suffix4_hash`');
CALL add_survey_phone_privacy_column('phone_normalized_version', '`phone_normalized_version` varchar(16) DEFAULT NULL AFTER `phone_region`');

DROP PROCEDURE IF EXISTS add_survey_phone_privacy_column;

UPDATE `survey`
SET `tenant_id` = 'default'
WHERE `tenant_id` IS NULL OR `tenant_id` = '';

UPDATE `survey`
SET `customer_uuid` = UUID()
WHERE `customer_uuid` IS NULL OR `customer_uuid` = '';

SET @idx_exists := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'survey'
    AND INDEX_NAME = 'idx_survey_tenant_phone_hash'
);
SET @ddl := IF(@idx_exists = 0,
  'ALTER TABLE `survey` ADD UNIQUE KEY `idx_survey_tenant_phone_hash` (`tenant_id`, `phone_hash`)',
  'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'survey'
    AND INDEX_NAME = 'idx_survey_tenant_phone_suffix4_hash'
);
SET @ddl := IF(@idx_exists = 0,
  'ALTER TABLE `survey` ADD KEY `idx_survey_tenant_phone_suffix4_hash` (`tenant_id`, `phone_suffix4_hash`)',
  'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS `role_permission` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `role_id` bigint NOT NULL,
  `permission` varchar(64) NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_permission` (`role_id`, `permission`),
  KEY `idx_role_permission_permission` (`permission`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT IGNORE INTO `role_permission` (`role_id`, `permission`)
SELECT `id`, 'PHONE_MASKED' FROM `sys_role`
WHERE `code` IN ('ADMIN', 'STAFF', 'LEADER', 'CAPTAIN', 'MANAGER');

INSERT IGNORE INTO `role_permission` (`role_id`, `permission`)
SELECT `id`, 'PHONE_VIEW_FULL' FROM `sys_role`
WHERE `code` = 'ADMIN';

INSERT IGNORE INTO `role_permission` (`role_id`, `permission`)
SELECT `id`, 'PHONE_EXPORT_FULL' FROM `sys_role`
WHERE `code` = 'ADMIN';

CREATE TABLE IF NOT EXISTS `personal_info_access_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` varchar(64) NOT NULL DEFAULT 'default',
  `customer_id` bigint DEFAULT NULL,
  `customer_uuid` char(36) DEFAULT NULL,
  `actor_username` varchar(50) NOT NULL,
  `action` varchar(64) NOT NULL,
  `permission` varchar(64) DEFAULT NULL,
  `reason` varchar(500) DEFAULT NULL,
  `success` tinyint(1) NOT NULL DEFAULT 1,
  `error_code` varchar(64) DEFAULT NULL,
  `ip_address` varchar(64) DEFAULT NULL,
  `user_agent` varchar(500) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_pial_actor_created` (`actor_username`, `created_at`),
  KEY `idx_pial_customer_created` (`tenant_id`, `customer_id`, `created_at`),
  KEY `idx_pial_action_created` (`action`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
