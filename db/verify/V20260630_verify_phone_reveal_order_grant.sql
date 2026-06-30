-- Read-only verification for V20260630_002_phone_reveal_order_grant.sql. No phone plaintext output.

SELECT table_name
FROM information_schema.tables
WHERE table_schema = DATABASE() AND table_name = 'phone_reveal_order_grant';

SELECT column_name
FROM information_schema.columns
WHERE table_schema = DATABASE() AND table_name = 'phone_reveal_order_grant'
ORDER BY column_name;

SELECT index_name
FROM information_schema.statistics
WHERE table_schema = DATABASE() AND table_name = 'phone_reveal_order_grant'
GROUP BY index_name;

-- Active (non-expired, enabled) grant count only; never selects survey phone fields.
SELECT COUNT(*) AS active_order_grants
FROM phone_reveal_order_grant
WHERE enabled = 1 AND expires_at > CURRENT_TIMESTAMP;
