-- Tenant/global system settings for phone display and survey list page size.
-- Target: MySQL 8, utf8mb4 / utf8mb4_0900_ai_ci.
-- Additive only: does not modify survey phone privacy columns or legacy survey.phone.
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `tenant_system_settings` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` varchar(64) NOT NULL DEFAULT 'default',
  `phone_display_policy` varchar(64) NOT NULL DEFAULT 'CLICK_TO_SESSION_VISIBLE',
  `order_page_size` int NOT NULL DEFAULT 20,
  `enabled` tinyint(1) NOT NULL DEFAULT 1,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_system_settings` (`tenant_id`),
  KEY `idx_tenant_enabled` (`tenant_id`, `enabled`),
  CONSTRAINT `chk_tenant_system_phone_policy`
    CHECK (`phone_display_policy` IN ('CLICK_TO_SESSION_VISIBLE', 'SINGLE_ORDER_TIMED_REVEAL', 'MASKED_ONLY')),
  CONSTRAINT `chk_tenant_system_order_page_size`
    CHECK (`order_page_size` BETWEEN 1 AND 100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT IGNORE INTO `tenant_system_settings`
  (`tenant_id`, `phone_display_policy`, `order_page_size`, `enabled`, `created_at`, `updated_at`)
VALUES
  ('default', 'CLICK_TO_SESSION_VISIBLE', 20, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT IGNORE INTO `role_permission` (`role_id`, `permission`)
SELECT `id`, 'PRIVACY_POLICY_MANAGE' FROM `sys_role`
WHERE `code` = 'ADMIN';
