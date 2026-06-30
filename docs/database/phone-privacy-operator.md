# Phone Privacy Operator Notes

Run order for existing databases:

1. Back up the database.
2. Run `db/migrations/V20260628_001_add_phone_privacy.sql`.
3. Run `phone-privacy-backfill --table=survey --tenantId=default --batchSize=500 --dryRun=true`.
4. If counts are expected, run the same command with `--dryRun=false`.
5. Run `db/migrations/V20260629_001_phone_reveal_session.sql`.
6. Run `db/verify/V20260628_verify_phone_privacy.sql` and `db/verify/V20260629_verify_phone_reveal_session.sql`.

Do not run `V20260628_002_clear_legacy_plain_phone.sql` until hash, mask, suffix4 hash, and encrypted fields are verified for legacy rows.

Rollback for `LOGIN_SESSION_SHOW_ALL`:

```sql
UPDATE privacy_phone_reveal_policy
SET allow_login_session_show_all = 0,
    reveal_visibility_mode = 'AUTO_HIDE',
    updated_at = CURRENT_TIMESTAMP;

UPDATE phone_reveal_login_session
SET enabled = 0,
    disabled_at = CURRENT_TIMESTAMP,
    disabled_reason = 'ROLLBACK',
    updated_at = CURRENT_TIMESTAMP
WHERE enabled = 1;
```

Keep encrypted columns and audit rows. Do not drop privacy tables during incident rollback.
