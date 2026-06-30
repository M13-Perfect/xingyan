-- First additive-only schema expansion for the sales workflow.
-- Target: MySQL 8, utf8mb4 / utf8mb4_0900_ai_ci.
-- Run after backup and rehearsal. This file intentionally avoids destructive DDL.
-- Apply with a utf8mb4 client connection (mysql --default-character-set=utf8mb4) or the
-- Chinese seed values below will be stored as mojibake.
SET NAMES utf8mb4;

ALTER TABLE `survey`
  ADD COLUMN `owner_user_id` int NULL AFTER `owner`,
  ADD COLUMN `created_by_user_id` int NULL AFTER `owner_user_id`,
  ADD COLUMN `updated_by_user_id` int NULL AFTER `created_by_user_id`,
  ADD COLUMN `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP AFTER `create_time`,
  ADD COLUMN `is_deleted` tinyint(1) NOT NULL DEFAULT 0 AFTER `updated_at`,
  ADD COLUMN `deleted_at` datetime NULL AFTER `is_deleted`,
  ADD COLUMN `deleted_by_user_id` int NULL AFTER `deleted_at`,
  ADD COLUMN `last_followup_at` datetime NULL AFTER `next_survey_date`,
  ADD COLUMN `last_followup_result` varchar(50) NULL AFTER `last_followup_at`,
  ADD COLUMN `intent_level` varchar(20) NULL AFTER `last_followup_result`,
  ADD COLUMN `source_channel` varchar(50) NULL AFTER `intent_level`,
  ADD INDEX `idx_survey_owner_user_status_create` (`owner_user_id`, `status`, `create_time`),
  ADD INDEX `idx_survey_deleted_owner_next` (`is_deleted`, `owner_user_id`, `next_survey_date`),
  ADD INDEX `idx_survey_last_followup` (`last_followup_at`);

ALTER TABLE `notice`
  ADD COLUMN `related_type` varchar(50) NULL AFTER `created_by`,
  ADD COLUMN `related_id` bigint NULL AFTER `related_type`,
  ADD COLUMN `action_url` varchar(255) NULL AFTER `related_id`,
  ADD INDEX `idx_notice_related` (`related_type`, `related_id`);

ALTER TABLE `notice_user_state`
  ADD COLUMN `user_id` int NULL AFTER `username`,
  ADD INDEX `idx_notice_state_user_id_filter` (`user_id`, `is_deleted`, `is_read`, `notice_id`);

CREATE TABLE IF NOT EXISTS `sys_role` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(50) NOT NULL,
  `name` varchar(50) NOT NULL,
  `description` varchar(255) NULL,
  `is_system` tinyint(1) NOT NULL DEFAULT 0,
  `is_enabled` tinyint(1) NOT NULL DEFAULT 1,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_role_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT IGNORE INTO `sys_role` (`code`, `name`, `description`, `is_system`, `is_enabled`)
VALUES
  ('ADMIN', '管理员', '系统管理员', 1, 1),
  ('STAFF', '业务员', '一线销售/回访人员', 1, 1),
  ('LEADER', '组长', '销售小组负责人', 1, 1),
  ('CAPTAIN', '队长', '销售队伍负责人', 1, 1),
  ('MANAGER', '经理', '销售管理人员', 1, 1);

CREATE TABLE IF NOT EXISTS `user_role` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` int NOT NULL,
  `role_id` bigint NOT NULL,
  `assigned_by_user_id` int NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `revoked_at` datetime NULL,
  PRIMARY KEY (`id`),
  KEY `idx_user_role_user` (`user_id`, `revoked_at`),
  KEY `idx_user_role_role` (`role_id`, `revoked_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `team` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(100) NOT NULL,
  `parent_id` bigint NULL,
  `type` varchar(20) NOT NULL,
  `leader_user_id` int NULL,
  `is_enabled` tinyint(1) NOT NULL DEFAULT 1,
  `sort_order` int NOT NULL DEFAULT 0,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_team_parent` (`parent_id`),
  KEY `idx_team_type_enabled` (`type`, `is_enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `user_team` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` int NOT NULL,
  `team_id` bigint NOT NULL,
  `role_in_team` varchar(20) NOT NULL DEFAULT 'MEMBER',
  `is_primary` tinyint(1) NOT NULL DEFAULT 1,
  `joined_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `left_at` datetime NULL,
  PRIMARY KEY (`id`),
  KEY `idx_user_team_user` (`user_id`, `left_at`),
  KEY `idx_user_team_team` (`team_id`, `left_at`),
  KEY `idx_user_team_role` (`role_in_team`, `left_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `survey_share` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `survey_id` bigint NOT NULL,
  `user_id` int NOT NULL,
  `permission` varchar(20) NOT NULL DEFAULT 'VIEW',
  `shared_by_user_id` int NULL,
  `reason` varchar(255) NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `revoked_at` datetime NULL,
  PRIMARY KEY (`id`),
  KEY `idx_survey_share_survey` (`survey_id`, `revoked_at`),
  KEY `idx_survey_share_user` (`user_id`, `permission`, `revoked_at`),
  KEY `idx_survey_share_operator` (`shared_by_user_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `followup_task` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `survey_id` bigint NOT NULL,
  `title` varchar(100) NOT NULL DEFAULT '回访任务',
  `task_type` varchar(20) NOT NULL DEFAULT 'FOLLOWUP',
  `round_no` int NULL,
  `assignee_user_id` int NULL,
  `status` varchar(20) NOT NULL DEFAULT 'PENDING',
  `priority` varchar(20) NOT NULL DEFAULT 'NORMAL',
  `due_at` datetime NULL,
  `accepted_at` datetime NULL,
  `started_at` datetime NULL,
  `completed_at` datetime NULL,
  `cancelled_at` datetime NULL,
  `result` varchar(50) NULL,
  `remark` text NULL,
  `created_by_user_id` int NULL,
  `updated_by_user_id` int NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted` tinyint(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_task_assignee_status_due` (`assignee_user_id`, `status`, `due_at`),
  KEY `idx_task_survey_status` (`survey_id`, `status`),
  KEY `idx_task_due_status` (`due_at`, `status`),
  KEY `idx_task_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `followup_record` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `survey_id` bigint NOT NULL,
  `task_id` bigint NULL,
  `operator_user_id` int NULL,
  `contact_method` varchar(20) NOT NULL DEFAULT 'PHONE',
  `contact_result` varchar(50) NOT NULL DEFAULT 'OTHER',
  `contacted_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `next_followup_at` datetime NULL,
  `content` text NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_record_survey_contacted` (`survey_id`, `contacted_at`),
  KEY `idx_record_task_contacted` (`task_id`, `contacted_at`),
  KEY `idx_record_operator_contacted` (`operator_user_id`, `contacted_at`),
  KEY `idx_record_next_followup` (`next_followup_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `followup_transfer` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `survey_id` bigint NOT NULL,
  `task_id` bigint NULL,
  `transfer_scope` varchar(20) NOT NULL DEFAULT 'TASK',
  `from_user_id` int NULL,
  `to_user_id` int NOT NULL,
  `requested_by_user_id` int NULL,
  `approved_by_user_id` int NULL,
  `status` varchar(20) NOT NULL DEFAULT 'APPROVED',
  `reason` varchar(500) NOT NULL,
  `approved_at` datetime NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_transfer_survey_created` (`survey_id`, `created_at`),
  KEY `idx_transfer_task_created` (`task_id`, `created_at`),
  KEY `idx_transfer_from_user` (`from_user_id`, `created_at`),
  KEY `idx_transfer_to_user` (`to_user_id`, `created_at`),
  KEY `idx_transfer_status_created` (`status`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `operation_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `actor_user_id` int NULL,
  `actor_username` varchar(50) NULL,
  `action` varchar(50) NOT NULL,
  `object_type` varchar(50) NOT NULL,
  `object_id` bigint NULL,
  `before_json` json NULL,
  `after_json` json NULL,
  `ip_address` varchar(64) NULL,
  `user_agent` varchar(500) NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_log_actor_created` (`actor_user_id`, `created_at`),
  KEY `idx_log_object_created` (`object_type`, `object_id`, `created_at`),
  KEY `idx_log_action_created` (`action`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
