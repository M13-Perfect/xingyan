-- Verification SQL for V20260626_001 and V20260626_002.
-- Run in the target schema after both migrations.

SELECT
  COUNT(*) AS survey_total,
  SUM(CASE WHEN `owner_user_id` IS NOT NULL THEN 1 ELSE 0 END) AS owner_user_id_filled,
  SUM(CASE WHEN `owner_user_id` IS NULL THEN 1 ELSE 0 END) AS owner_user_id_missing
FROM `survey`;

SELECT s.`id`, s.`owner`
FROM `survey` s
LEFT JOIN `user` u ON u.`username` = s.`owner`
WHERE s.`owner` IS NOT NULL
  AND s.`owner` <> ''
  AND u.`id` IS NULL
ORDER BY s.`id`
LIMIT 100;

SELECT
  COUNT(*) AS notice_state_total,
  SUM(CASE WHEN `user_id` IS NOT NULL THEN 1 ELSE 0 END) AS user_id_filled,
  SUM(CASE WHEN `user_id` IS NULL THEN 1 ELSE 0 END) AS user_id_missing
FROM `notice_user_state`;

SELECT r.`code`, COUNT(ur.`id`) AS active_user_role_count
FROM `sys_role` r
LEFT JOIN `user_role` ur ON ur.`role_id` = r.`id` AND ur.`revoked_at` IS NULL
GROUP BY r.`code`
ORDER BY r.`code`;

SELECT t.expected_table, IF(real_tables.table_name IS NULL, 'MISSING', 'OK') AS status
FROM (
  SELECT 'sys_role' AS expected_table UNION ALL
  SELECT 'user_role' UNION ALL
  SELECT 'team' UNION ALL
  SELECT 'user_team' UNION ALL
  SELECT 'survey_share' UNION ALL
  SELECT 'followup_task' UNION ALL
  SELECT 'followup_record' UNION ALL
  SELECT 'followup_transfer' UNION ALL
  SELECT 'operation_log'
) t
LEFT JOIN information_schema.tables real_tables
  ON real_tables.table_schema = DATABASE()
  AND real_tables.table_name = t.expected_table
ORDER BY t.expected_table;

SELECT
  i.table_name,
  i.index_name,
  GROUP_CONCAT(i.column_name ORDER BY i.seq_in_index) AS columns_in_index
FROM information_schema.statistics i
WHERE i.table_schema = DATABASE()
  AND i.index_name IN (
    'idx_survey_owner_user_status_create',
    'idx_survey_deleted_owner_next',
    'idx_survey_last_followup',
    'idx_notice_related',
    'idx_notice_state_user_id_filter',
    'idx_user_role_user',
    'idx_user_role_role',
    'idx_team_parent',
    'idx_team_type_enabled',
    'idx_user_team_user',
    'idx_user_team_team',
    'idx_user_team_role',
    'idx_survey_share_survey',
    'idx_survey_share_user',
    'idx_survey_share_operator',
    'idx_task_assignee_status_due',
    'idx_task_survey_status',
    'idx_task_due_status',
    'idx_task_created',
    'idx_record_survey_contacted',
    'idx_record_task_contacted',
    'idx_record_operator_contacted',
    'idx_record_next_followup',
    'idx_transfer_survey_created',
    'idx_transfer_task_created',
    'idx_transfer_from_user',
    'idx_transfer_to_user',
    'idx_transfer_status_created',
    'idx_log_actor_created',
    'idx_log_object_created',
    'idx_log_action_created'
  )
GROUP BY i.table_name, i.index_name
ORDER BY i.table_name, i.index_name;
