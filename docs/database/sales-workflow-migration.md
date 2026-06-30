# Sales Workflow Database Migration

## 当前问题

- `survey.owner` 和 `notice_user_state.username` 仍用用户名字符串关联 `user.username`，后续改名、协作转派和权限查询成本高。
- `survey.shared_users` 依赖逗号字符串和 `FIND_IN_SET`，B-Tree 索引无法有效支撑规模化权限过滤。
- `SurveyMapper` 原有列表查询使用 `SELECT *`，新增列会扩大映射面；本次已改成显式列。
- 根目录 `xyyx_db.sql` 是本地初始化 dump，包含 `DROP TABLE IF EXISTS`，不能当生产迁移脚本。

## 本次目标

- 只做 additive-only 兼容升级：新增字段、索引、表和初始化角色。
- 保持旧接口和旧字段含义不变，旧后端 INSERT 仍可依赖新字段默认值或 NULL。
- 为角色、团队、共享、回访任务、回访记录、转派和操作日志预留规范化结构。

## 保留旧字段

`owner`、`shared_users`、`status`、`next_survey_date`、`remarks` 继续保留，因为当前线上接口和前端页面仍依赖它们。第一轮只新增 `owner_user_id`、`survey_share`、`followup_task` 等兼容结构，后端双写稳定后再逐步迁移读取路径。

## 第一轮不强加外键

历史库可能存在 `survey.owner` 找不到 `user.username`、通知状态用户名残留等脏数据。第一轮强制外键会让生产迁移直接失败。先回填、验证、清洗，再在后续版本按表分批加约束。

## 第一轮不批量生成 followup_task

旧 `survey` 的 `status`、`next_survey_date` 不能完整表达任务轮次、负责人确认、转派审批和取消状态。直接批量生成任务容易把历史客户误推到业务员工作区。本轮只建表，不自动造任务。

## 上线步骤

1. 备份生产数据库，确认备份可恢复。
2. 在测试库导入生产备份或等量脱敏数据演练。
3. 执行 `db/migrations/V20260626_001_expand_sales_workflow.sql`。
4. 执行 `db/migrations/V20260626_002_backfill_sales_workflow_compat.sql`。
5. 执行 `db/verify/V20260626_verify_sales_workflow.sql` 并保存结果。
6. 验证旧功能：登录、客户列表、客户新增、客户处理、客户删除、改回访日期、共享、备注、通知列表、通知已读/删除。
7. 再部署后端双写版本：创建客户写 `owner` 和 `owner_user_id`，通知状态写 `username` 和 `user_id`。

Windows 本机 MySQL CLI 执行时加 `--default-character-set=utf8mb4`，否则中文默认值可能解析失败。

## 验证 SQL 摘要

- `survey` 总数、`owner_user_id` 已回填数、未回填数。
- `owner` 不为空但匹配不到 `user.username` 的记录。
- `notice_user_state.user_id` 回填数量。
- `user_role` 按角色初始化数量。
- 新表是否存在。
- 关键索引是否存在。

完整脚本见 `db/verify/V20260626_verify_sales_workflow.sql`。

## 回滚说明

MySQL DDL 多数会自动提交，不能承诺事务级完整回滚。生产回滚优先依赖迁移前备份恢复。

如必须逻辑回滚，只能在确认后端未依赖新结构后执行人工清理：删除本轮新增表、删除本轮新增字段和索引。该操作本身也是破坏性 DDL，必须先备份并在测试库演练。

## 执行前检查

- 确认没有把 `xyyx_db.sql` 或 `admin.sql` 接入生产部署。
- 确认生产库字符集/排序规则为 `utf8mb4` / `utf8mb4_0900_ai_ci` 或兼容配置。
- 确认 `user.username` 与 Casdoor 用户名、本地业务员账号一致。
- 记录 `survey.owner` 匹配失败数据，迁移后单独清洗。
