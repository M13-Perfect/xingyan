# Sales Workflow Backend TODO

1. 创建客户时双写 `survey.owner` 和 `survey.owner_user_id`。
2. 新增回访记录时写 `followup_record`，同时按需更新 `survey.last_followup_at` / `last_followup_result`。
3. 修改客户关键字段时写 `operation_log`，避免日志里记录明文密码、令牌或无关敏感数据。
4. 转派任务时写 `followup_transfer`，并更新对应 `followup_task.assignee_user_id`。
5. 通知关联业务对象时写 `notice.related_type` / `related_id` / `action_url`。
6. 权限查询兼容 `owner_user_id` 和 `owner`：优先用 `owner_user_id`，缺失时回退到 `owner`。
7. 后续逐步用 `survey_share` 替代 `shared_users`，迁移完成前保持双写和回退读取。
