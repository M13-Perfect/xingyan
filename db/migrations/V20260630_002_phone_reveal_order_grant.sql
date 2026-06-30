-- V20260630_002_phone_reveal_order_grant.sql
-- Additive, idempotent. Backs the OFF state of the "手机号隐私设置" toggle:
-- GlobalPhoneDisplayPolicyMode.SINGLE_ORDER_TIMED_REVEAL = single-order, 300s timed reveal.
-- Each row grants full-phone display for ONE survey, scoped to one user + one login session,
-- expiring after 300 seconds. No DROP/RENAME, no row mutation of existing tables.

CREATE TABLE IF NOT EXISTS phone_reveal_order_grant (
  id BIGINT NOT NULL AUTO_INCREMENT,
  tenant_id VARCHAR(64) NOT NULL,
  user_id BIGINT NOT NULL,
  login_session_id_hash VARCHAR(64) NOT NULL,
  survey_id BIGINT NOT NULL,
  enabled TINYINT(1) NOT NULL DEFAULT 1,
  expires_at DATETIME NOT NULL,
  disabled_at DATETIME NULL,
  disabled_reason VARCHAR(64) NULL,
  ip_address VARCHAR(64) NULL,
  user_agent VARCHAR(500) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_order_grant (tenant_id, user_id, login_session_id_hash, survey_id),
  KEY idx_order_grant_lookup (tenant_id, user_id, enabled, expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Per-order timed full-phone reveal grants (OFF / SINGLE_ORDER_TIMED_REVEAL).';
