#!/bin/sh
# 全新建库时的增量补齐。xyyx_db.sql 是"接近完整"的当前 schema（V20260626/28 与
# V20260701_001 的内容已折叠进去），但以下迁移建的表从未折叠进 bootstrap
# （见 CURRENT_TASKS.md 已知差距），需要在 bootstrap 之后按序补跑。
# 这些迁移都带幂等防护（CREATE TABLE IF NOT EXISTS / information_schema 列守卫），
# 将来若把它们折叠进 xyyx_db.sql，这里会安全地空跑。
# 注意：不能无脑跑 /migrations/*.sql —— 已折叠进 bootstrap 的老迁移（如
# V20260701_001 的 DROP INDEX）在全新库上会硬失败。新增迁移时：要么折叠进
# xyyx_db.sql，要么追加到下面的列表。
set -e
for f in \
  V20260629_001_phone_reveal_session.sql \
  V20260630_001_tenant_system_settings.sql \
  V20260630_002_phone_reveal_order_grant.sql \
  V20260630_003_fix_phone_policy_check.sql \
  V20260701_002_add_revisit_deadline_days.sql \
; do
  echo "[migrations] applying $f"
  mysql --default-character-set=utf8mb4 -uroot -p"$MYSQL_ROOT_PASSWORD" xyyx_db < "/migrations/$f"
done
echo "[migrations] all done"
