/*
  xyyx_db bootstrap script
  date: 2026-05-28
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

DROP TABLE IF EXISTS `notice_user_state`;
DROP TABLE IF EXISTS `notice`;
DROP TABLE IF EXISTS `survey`;
DROP TABLE IF EXISTS `user`;

-- -------------------------------------
-- user
-- -------------------------------------
CREATE TABLE `user` (
  `id` int NOT NULL AUTO_INCREMENT,
  `username` varchar(50) NOT NULL,
  `password` varchar(255) NOT NULL,
  `role` varchar(20) NOT NULL COMMENT 'admin/staff',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_username` (`username`),
  CONSTRAINT `ck_user_role` CHECK (`role` IN ('admin', 'staff'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- -------------------------------------
-- survey
-- -------------------------------------
CREATE TABLE `survey` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(50) NOT NULL DEFAULT '-',
  `phone` varchar(20) DEFAULT NULL,
  `city` varchar(50) NOT NULL DEFAULT '-',
  `project` varchar(100) NOT NULL DEFAULT '-',
  `wechat` varchar(50) DEFAULT NULL,
  `budget` varchar(50) NOT NULL DEFAULT '-',
  `owner` varchar(50) DEFAULT NULL,
  `remarks` text,
  `status` varchar(20) DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `next_survey_date` datetime DEFAULT NULL,
  `visibility` varchar(20) NOT NULL DEFAULT 'PRIVATE',
  `shared_users` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_survey_phone` (`phone`),
  UNIQUE KEY `uk_survey_wechat` (`wechat`),
  KEY `idx_survey_status_create` (`status`, `create_time`),
  KEY `idx_survey_owner_status_create` (`owner`, `status`, `create_time`),
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
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `is_deleted` tinyint(1) NOT NULL DEFAULT 0,
  `deleted_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
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
  `is_read` tinyint(1) NOT NULL DEFAULT 0,
  `read_at` datetime DEFAULT NULL,
  `is_deleted` tinyint(1) NOT NULL DEFAULT 0,
  `deleted_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_notice_user` (`notice_id`, `username`),
  KEY `idx_user_state_filter` (`username`, `is_deleted`, `is_read`, `notice_id`),
  CONSTRAINT `fk_notice_state_notice`
    FOREIGN KEY (`notice_id`) REFERENCES `notice` (`id`)
    ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT `fk_notice_state_user`
    FOREIGN KEY (`username`) REFERENCES `user` (`username`)
    ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- -------------------------------------
-- seed data
-- -------------------------------------
INSERT INTO `user` (`username`, `password`, `role`) VALUES
('18007300157', '$2a$10$3Gyun6Md352S6lpRntWuD.wL/jjj1/2kWWs0APmSS3yMP4Wsy7oAC', 'admin');

INSERT INTO `notice` (`title`, `content`, `level`, `created_by`) VALUES
('Notification Center Enabled', 'Use the notification center to review unread and read notices, then process in batch.', 'INFO', '18007300157'),
('Daily Follow-up Reminder', 'Check pending follow-up records every day and close overdue tasks promptly.', 'WARN', '18007300157');

SET FOREIGN_KEY_CHECKS = 1;
