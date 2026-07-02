-- Verify V20260701_002_add_revisit_deadline_days.sql applied cleanly.
SELECT COUNT(*) AS column_present
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'tenant_system_settings'
  AND column_name = 'revisit_deadline_days';

SELECT COUNT(*) AS check_constraint_present
FROM information_schema.table_constraints
WHERE table_schema = DATABASE()
  AND table_name = 'tenant_system_settings'
  AND constraint_name = 'chk_tenant_system_revisit_deadline_days';

SELECT tenant_id, revisit_deadline_days FROM tenant_system_settings;
