-- Phone reveal policy and login-session show-all state.
-- Target: MySQL 8, utf8mb4 / utf8mb4_0900_ai_ci.
-- Additive only: does not clear or delete legacy survey.phone.
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `privacy_phone_reveal_policy` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` varchar(64) NOT NULL DEFAULT 'default',
  `reveal_visibility_mode` varchar(64) NOT NULL DEFAULT 'AUTO_HIDE',
  `auto_hide_seconds` int NOT NULL DEFAULT 60,
  `extended_duration_seconds` int NOT NULL DEFAULT 600,
  `allow_page_session_revealed_only` tinyint(1) NOT NULL DEFAULT 0,
  `allow_login_session_show_all` tinyint(1) NOT NULL DEFAULT 0,
  `login_session_show_all_max_seconds` int NOT NULL DEFAULT 28800,
  `login_session_show_all_requires_permission` tinyint(1) NOT NULL DEFAULT 1,
  `reveal_rate_limit_seconds` int NOT NULL DEFAULT 5,
  `per_user_minute_limit` int NOT NULL DEFAULT 20,
  `per_ip_minute_limit` int NOT NULL DEFAULT 60,
  `session_batch_decrypt_per_user_minute_limit` int NOT NULL DEFAULT 500,
  `session_batch_decrypt_per_ip_minute_limit` int NOT NULL DEFAULT 1000,
  `session_batch_max_page_size` int NOT NULL DEFAULT 50,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_phone_reveal_policy` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT IGNORE INTO `privacy_phone_reveal_policy` (`tenant_id`)
VALUES ('default');

CREATE TABLE IF NOT EXISTS `phone_reveal_login_session` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` varchar(64) NOT NULL DEFAULT 'default',
  `user_id` bigint NOT NULL,
  `login_session_id_hash` char(64) NOT NULL,
  `enabled` tinyint(1) NOT NULL DEFAULT 0,
  `enabled_at` datetime DEFAULT NULL,
  `expires_at` datetime NOT NULL,
  `disabled_at` datetime DEFAULT NULL,
  `disabled_reason` varchar(128) DEFAULT NULL,
  `policy_version` bigint DEFAULT NULL,
  `ip_address` varchar(64) DEFAULT NULL,
  `user_agent` varchar(512) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_user_login_session` (`tenant_id`, `user_id`, `login_session_id_hash`),
  KEY `idx_expires_at` (`expires_at`),
  KEY `idx_user_enabled` (`tenant_id`, `user_id`, `enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

DROP PROCEDURE IF EXISTS add_pial_phone_reveal_column;
DELIMITER //
CREATE PROCEDURE add_pial_phone_reveal_column(
  IN p_column_name varchar(64),
  IN p_column_ddl text
)
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'personal_info_access_log'
      AND COLUMN_NAME = p_column_name
  ) THEN
    SET @ddl = CONCAT('ALTER TABLE `personal_info_access_log` ADD COLUMN ', p_column_ddl);
    PREPARE stmt FROM @ddl;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
  END IF;
END//
DELIMITER ;

CALL add_pial_phone_reveal_column('operator_user_id', '`operator_user_id` bigint DEFAULT NULL AFTER `actor_username`');
CALL add_pial_phone_reveal_column('login_session_id_hash', '`login_session_id_hash` char(64) DEFAULT NULL AFTER `operator_user_id`');
CALL add_pial_phone_reveal_column('target_type', '`target_type` varchar(64) DEFAULT NULL AFTER `permission`');
CALL add_pial_phone_reveal_column('target_ids_json', '`target_ids_json` text DEFAULT NULL AFTER `target_type`');
CALL add_pial_phone_reveal_column('record_count', '`record_count` int DEFAULT NULL AFTER `target_ids_json`');
CALL add_pial_phone_reveal_column('query_hash', '`query_hash` char(64) DEFAULT NULL AFTER `record_count`');
CALL add_pial_phone_reveal_column('page', '`page` int DEFAULT NULL AFTER `query_hash`');
CALL add_pial_phone_reveal_column('page_size', '`page_size` int DEFAULT NULL AFTER `page`');
CALL add_pial_phone_reveal_column('reveal_visibility_mode', '`reveal_visibility_mode` varchar(64) DEFAULT NULL AFTER `page_size`');
CALL add_pial_phone_reveal_column('request_id', '`request_id` varchar(80) DEFAULT NULL AFTER `user_agent`');

DROP PROCEDURE IF EXISTS add_pial_phone_reveal_column;

SET @idx_exists := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'personal_info_access_log'
    AND INDEX_NAME = 'idx_pial_request_id'
);
SET @ddl := IF(@idx_exists = 0,
  'ALTER TABLE `personal_info_access_log` ADD KEY `idx_pial_request_id` (`request_id`)',
  'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'personal_info_access_log'
    AND INDEX_NAME = 'idx_pial_login_session'
);
SET @ddl := IF(@idx_exists = 0,
  'ALTER TABLE `personal_info_access_log` ADD KEY `idx_pial_login_session` (`tenant_id`, `operator_user_id`, `login_session_id_hash`, `created_at`)',
  'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

INSERT IGNORE INTO `role_permission` (`role_id`, `permission`)
SELECT `id`, 'PHONE_VIEW_FULL_SESSION' FROM `sys_role`
WHERE `code` = 'ADMIN';

INSERT IGNORE INTO `role_permission` (`role_id`, `permission`)
SELECT `id`, 'PRIVACY_POLICY_MANAGE' FROM `sys_role`
WHERE `code` = 'ADMIN';
