/*
  xyyx_db bootstrap script
  date: 2026-06-26
  notes:
  - Re-runnable (drops and recreates tables)
  - Includes notification center schema and seed rows
*/

CREATE DATABASE IF NOT EXISTS `xyyx_db`
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

USE `xyyx_db`;

SET NAMES utf8mb4;
SET time_zone = '+08:00';
SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS `operation_log`;
DROP TABLE IF EXISTS `personal_info_access_log`;
DROP TABLE IF EXISTS `followup_transfer`;
DROP TABLE IF EXISTS `followup_record`;
DROP TABLE IF EXISTS `followup_task`;
DROP TABLE IF EXISTS `survey_share`;
DROP TABLE IF EXISTS `user_team`;
DROP TABLE IF EXISTS `team`;
DROP TABLE IF EXISTS `role_permission`;
DROP TABLE IF EXISTS `user_role`;
DROP TABLE IF EXISTS `notice_user_state`;
DROP TABLE IF EXISTS `notice`;
DROP TABLE IF EXISTS `survey`;
DROP TABLE IF EXISTS `sys_role`;
DROP TABLE IF EXISTS `user`;

-- -------------------------------------
-- user
-- -------------------------------------
CREATE TABLE `user` (
  `id` int NOT NULL AUTO_INCREMENT,
  `tenant_id` varchar(64) NOT NULL DEFAULT 'default',
  `username` varchar(50) NOT NULL,
  `password` varchar(255) NOT NULL,
  `role` varchar(20) NOT NULL COMMENT 'admin/staff; deprecated, prefer user_role',
  `status` varchar(20) NOT NULL DEFAULT 'active',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted` tinyint(1) NOT NULL DEFAULT 0,
  `deleted_at` datetime DEFAULT NULL,
  `created_by_user_id` int DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_tenant_username` (`tenant_id`, `username`),
  -- fk_notice_state_user 引用 user(username)，外键要求该列是某个索引的首列；
  -- uk_user_tenant_username 里 username 是第二列，不满足，故保留此普通索引
  KEY `idx_user_username` (`username`),
  CONSTRAINT `ck_user_role` CHECK (`role` IN ('admin', 'staff')),
  CONSTRAINT `ck_user_status` CHECK (`status` IN ('active', 'disabled'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- -------------------------------------
-- sys_role
-- -------------------------------------
CREATE TABLE `sys_role` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(50) NOT NULL,
  `name` varchar(50) NOT NULL,
  `description` varchar(255) DEFAULT NULL,
  `is_system` tinyint(1) NOT NULL DEFAULT 0,
  `is_enabled` tinyint(1) NOT NULL DEFAULT 1,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_role_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- -------------------------------------
-- survey
-- -------------------------------------
CREATE TABLE `survey` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` varchar(64) NOT NULL DEFAULT 'default',
  `customer_uuid` char(36) DEFAULT NULL,
  `name` varchar(50) NOT NULL DEFAULT '-',
  `phone` varchar(20) DEFAULT NULL,
  `phone_ciphertext` varbinary(255) DEFAULT NULL,
  `phone_iv` binary(12) DEFAULT NULL,
  `phone_tag` binary(16) DEFAULT NULL,
  `phone_enc_key_version` varchar(32) DEFAULT NULL,
  `phone_hash` binary(32) DEFAULT NULL,
  `phone_hash_key_version` varchar(32) DEFAULT NULL,
  `phone_mask` varchar(32) DEFAULT NULL,
  `phone_suffix4_hash` binary(32) DEFAULT NULL,
  `phone_region` varchar(8) DEFAULT NULL,
  `phone_normalized_version` varchar(16) DEFAULT NULL,
  `city` varchar(50) NOT NULL DEFAULT '-',
  `project` varchar(100) NOT NULL DEFAULT '-',
  `wechat` varchar(50) DEFAULT NULL,
  `social_account` varchar(100) DEFAULT NULL,
  `budget` varchar(50) NOT NULL DEFAULT '-',
  `owner` varchar(50) DEFAULT NULL,
  `owner_user_id` int DEFAULT NULL,
  `created_by_user_id` int DEFAULT NULL,
  `updated_by_user_id` int DEFAULT NULL,
  `remarks` text,
  `status` varchar(20) DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted` tinyint(1) NOT NULL DEFAULT 0,
  `deleted_at` datetime DEFAULT NULL,
  `deleted_by_user_id` int DEFAULT NULL,
  `next_survey_date` datetime DEFAULT NULL,
  `last_followup_at` datetime DEFAULT NULL,
  `last_followup_result` varchar(50) DEFAULT NULL,
  `intent_level` varchar(20) DEFAULT NULL,
  `source_channel` varchar(50) DEFAULT NULL,
  `visibility` varchar(20) NOT NULL DEFAULT 'PRIVATE',
  `shared_users` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_survey_phone` (`phone`),
  UNIQUE KEY `idx_survey_tenant_phone_hash` (`tenant_id`, `phone_hash`),
  UNIQUE KEY `uk_survey_wechat` (`wechat`),
  KEY `idx_survey_tenant_phone_suffix4_hash` (`tenant_id`, `phone_suffix4_hash`),
  KEY `idx_survey_status_create` (`status`, `create_time`),
  KEY `idx_survey_owner_status_create` (`owner`, `status`, `create_time`),
  KEY `idx_survey_owner_user_status_create` (`owner_user_id`, `status`, `create_time`),
  KEY `idx_survey_deleted_owner_next` (`is_deleted`, `owner_user_id`, `next_survey_date`),
  KEY `idx_survey_last_followup` (`last_followup_at`),
  KEY `idx_survey_visibility_status_create` (`visibility`, `status`, `create_time`),
  KEY `idx_survey_next_date` (`next_survey_date`),
  CONSTRAINT `ck_survey_visibility` CHECK (`visibility` IN ('PRIVATE', 'PUBLIC', 'CUSTOM'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- -------------------------------------
-- notice
-- -------------------------------------
CREATE TABLE `notice` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `title` varchar(120) NOT NULL,
  `content` varchar(500) NOT NULL,
  `level` varchar(20) NOT NULL DEFAULT 'INFO' COMMENT 'INFO/WARN/ALERT',
  `created_by` varchar(50) NOT NULL,
  `related_type` varchar(50) DEFAULT NULL,
  `related_id` bigint DEFAULT NULL,
  `action_url` varchar(255) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `is_deleted` tinyint(1) NOT NULL DEFAULT 0,
  `deleted_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_notice_related` (`related_type`, `related_id`),
  KEY `idx_notice_deleted_created` (`is_deleted`, `created_at`),
  KEY `idx_notice_level_created` (`level`, `created_at`),
  CONSTRAINT `ck_notice_level` CHECK (`level` IN ('INFO', 'WARN', 'ALERT'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- -------------------------------------
-- notice_user_state
-- -------------------------------------
CREATE TABLE `notice_user_state` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `notice_id` bigint NOT NULL,
  `username` varchar(50) NOT NULL,
  `user_id` int DEFAULT NULL,
  `is_read` tinyint(1) NOT NULL DEFAULT 0,
  `read_at` datetime DEFAULT NULL,
  `is_deleted` tinyint(1) NOT NULL DEFAULT 0,
  `deleted_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_notice_user` (`notice_id`, `username`),
  KEY `idx_user_state_filter` (`username`, `is_deleted`, `is_read`, `notice_id`),
  KEY `idx_notice_state_user_id_filter` (`user_id`, `is_deleted`, `is_read`, `notice_id`),
  CONSTRAINT `fk_notice_state_notice`
    FOREIGN KEY (`notice_id`) REFERENCES `notice` (`id`)
    ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT `fk_notice_state_user`
    FOREIGN KEY (`username`) REFERENCES `user` (`username`)
    ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- -------------------------------------
-- user_role
-- -------------------------------------
CREATE TABLE `user_role` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` int NOT NULL,
  `tenant_id` varchar(64) NOT NULL DEFAULT 'default',
  `role_id` bigint NOT NULL,
  `assigned_by_user_id` int DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `revoked_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_user_role_user` (`user_id`, `revoked_at`),
  KEY `idx_user_role_role` (`role_id`, `revoked_at`),
  KEY `idx_user_role_tenant` (`tenant_id`, `revoked_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- -------------------------------------
-- role_permission
-- -------------------------------------
CREATE TABLE `role_permission` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `role_id` bigint NOT NULL,
  `permission` varchar(64) NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_permission` (`role_id`, `permission`),
  KEY `idx_role_permission_permission` (`permission`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- -------------------------------------
-- team
-- -------------------------------------
CREATE TABLE `team` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(100) NOT NULL,
  `parent_id` bigint DEFAULT NULL,
  `type` varchar(20) NOT NULL,
  `leader_user_id` int DEFAULT NULL,
  `is_enabled` tinyint(1) NOT NULL DEFAULT 1,
  `sort_order` int NOT NULL DEFAULT 0,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_team_parent` (`parent_id`),
  KEY `idx_team_type_enabled` (`type`, `is_enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- -------------------------------------
-- user_team
-- -------------------------------------
CREATE TABLE `user_team` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` int NOT NULL,
  `team_id` bigint NOT NULL,
  `role_in_team` varchar(20) NOT NULL DEFAULT 'MEMBER',
  `is_primary` tinyint(1) NOT NULL DEFAULT 1,
  `joined_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `left_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_user_team_user` (`user_id`, `left_at`),
  KEY `idx_user_team_team` (`team_id`, `left_at`),
  KEY `idx_user_team_role` (`role_in_team`, `left_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- -------------------------------------
-- survey_share
-- -------------------------------------
CREATE TABLE `survey_share` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `survey_id` bigint NOT NULL,
  `user_id` int NOT NULL,
  `permission` varchar(20) NOT NULL DEFAULT 'VIEW',
  `shared_by_user_id` int DEFAULT NULL,
  `reason` varchar(255) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `revoked_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_survey_share_survey` (`survey_id`, `revoked_at`),
  KEY `idx_survey_share_user` (`user_id`, `permission`, `revoked_at`),
  KEY `idx_survey_share_operator` (`shared_by_user_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- -------------------------------------
-- followup_task
-- -------------------------------------
CREATE TABLE `followup_task` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `survey_id` bigint NOT NULL,
  `title` varchar(100) NOT NULL DEFAULT '回访任务',
  `task_type` varchar(20) NOT NULL DEFAULT 'FOLLOWUP',
  `round_no` int DEFAULT NULL,
  `assignee_user_id` int DEFAULT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'PENDING',
  `priority` varchar(20) NOT NULL DEFAULT 'NORMAL',
  `due_at` datetime DEFAULT NULL,
  `accepted_at` datetime DEFAULT NULL,
  `started_at` datetime DEFAULT NULL,
  `completed_at` datetime DEFAULT NULL,
  `cancelled_at` datetime DEFAULT NULL,
  `result` varchar(50) DEFAULT NULL,
  `remark` text,
  `created_by_user_id` int DEFAULT NULL,
  `updated_by_user_id` int DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted` tinyint(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_task_assignee_status_due` (`assignee_user_id`, `status`, `due_at`),
  KEY `idx_task_survey_status` (`survey_id`, `status`),
  KEY `idx_task_due_status` (`due_at`, `status`),
  KEY `idx_task_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- -------------------------------------
-- followup_record
-- -------------------------------------
CREATE TABLE `followup_record` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `survey_id` bigint NOT NULL,
  `task_id` bigint DEFAULT NULL,
  `operator_user_id` int DEFAULT NULL,
  `contact_method` varchar(20) NOT NULL DEFAULT 'PHONE',
  `contact_result` varchar(50) NOT NULL DEFAULT 'OTHER',
  `contacted_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `next_followup_at` datetime DEFAULT NULL,
  `content` text,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_record_survey_contacted` (`survey_id`, `contacted_at`),
  KEY `idx_record_task_contacted` (`task_id`, `contacted_at`),
  KEY `idx_record_operator_contacted` (`operator_user_id`, `contacted_at`),
  KEY `idx_record_next_followup` (`next_followup_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- -------------------------------------
-- followup_transfer
-- -------------------------------------
CREATE TABLE `followup_transfer` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `survey_id` bigint NOT NULL,
  `task_id` bigint DEFAULT NULL,
  `transfer_scope` varchar(20) NOT NULL DEFAULT 'TASK',
  `from_user_id` int DEFAULT NULL,
  `to_user_id` int NOT NULL,
  `requested_by_user_id` int DEFAULT NULL,
  `approved_by_user_id` int DEFAULT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'APPROVED',
  `reason` varchar(500) NOT NULL,
  `approved_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_transfer_survey_created` (`survey_id`, `created_at`),
  KEY `idx_transfer_task_created` (`task_id`, `created_at`),
  KEY `idx_transfer_from_user` (`from_user_id`, `created_at`),
  KEY `idx_transfer_to_user` (`to_user_id`, `created_at`),
  KEY `idx_transfer_status_created` (`status`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- -------------------------------------
-- operation_log
-- -------------------------------------
CREATE TABLE `operation_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `actor_user_id` int DEFAULT NULL,
  `actor_username` varchar(50) DEFAULT NULL,
  `action` varchar(50) NOT NULL,
  `object_type` varchar(50) NOT NULL,
  `object_id` bigint DEFAULT NULL,
  `before_json` json DEFAULT NULL,
  `after_json` json DEFAULT NULL,
  `ip_address` varchar(64) DEFAULT NULL,
  `user_agent` varchar(500) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_log_actor_created` (`actor_user_id`, `created_at`),
  KEY `idx_log_object_created` (`object_type`, `object_id`, `created_at`),
  KEY `idx_log_action_created` (`action`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- -------------------------------------
-- personal_info_access_log
-- -------------------------------------
CREATE TABLE `personal_info_access_log` (
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

-- -------------------------------------
-- seed data
-- -------------------------------------
INSERT INTO `sys_role` (`code`, `name`, `description`, `is_system`, `is_enabled`) VALUES
('ADMIN', '管理员', '系统管理员', 1, 1),
('STAFF', '业务员', '一线销售/回访人员', 1, 1),
('LEADER', '组长', '销售小组负责人', 1, 1),
('CAPTAIN', '队长', '销售队伍负责人', 1, 1),
('MANAGER', '经理', '销售管理人员', 1, 1);

INSERT INTO `user` (`username`, `password`, `role`) VALUES
('18007300157', '$2a$10$3Gyun6Md352S6lpRntWuD.wL/jjj1/2kWWs0APmSS3yMP4Wsy7oAC', 'admin');

INSERT INTO `notice` (`title`, `content`, `level`, `created_by`) VALUES
('Notification Center Enabled', 'Use the notification center to review unread and read notices, then process in batch.', 'INFO', '18007300157'),
('Daily Follow-up Reminder', 'Check pending follow-up records every day and close overdue tasks promptly.', 'WARN', '18007300157');

INSERT INTO `user_role` (`user_id`, `role_id`, `assigned_by_user_id`, `revoked_at`)
SELECT u.`id`, r.`id`, NULL, NULL
FROM `user` u
JOIN `sys_role` r ON r.`code` = CASE LOWER(u.`role`)
  WHEN 'admin' THEN 'ADMIN'
  WHEN 'staff' THEN 'STAFF'
END
WHERE LOWER(u.`role`) IN ('admin', 'staff');

INSERT INTO `role_permission` (`role_id`, `permission`)
SELECT `id`, 'PHONE_MASKED' FROM `sys_role`
WHERE `code` IN ('ADMIN', 'STAFF', 'LEADER', 'CAPTAIN', 'MANAGER');

INSERT INTO `role_permission` (`role_id`, `permission`)
SELECT `id`, 'PHONE_VIEW_FULL' FROM `sys_role`
WHERE `code` = 'ADMIN';

INSERT INTO `role_permission` (`role_id`, `permission`)
SELECT `id`, 'PHONE_EXPORT_FULL' FROM `sys_role`
WHERE `code` = 'ADMIN';

SET FOREIGN_KEY_CHECKS = 1;
