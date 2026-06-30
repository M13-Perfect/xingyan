# Database Migrations

## Phone Privacy Backfill

For existing `survey.phone` data, run only after `V20260628_001_add_phone_privacy.sql`
has been applied and verified with `db/verify/V20260628_verify_phone_privacy.sql`.

Required private runtime config:

- `XYYX_PHONE_ENC_KEYS`
- `XYYX_PHONE_HASH_KEYS`
- optional `XYYX_PHONE_ENC_KEY_VERSION`
- optional `XYYX_PHONE_HASH_KEY_VERSION`

Do not print these values, commit them, or use one-off test keys for
`dryRun=false`. Local imported legacy data may need `--legacyDigits=true`; this
keeps new writes strict while allowing old digit-like values to be hashed.

Dry run first:

```powershell
mvn -q -DskipTests spring-boot:run "-Dspring-boot.run.arguments=phone-privacy-backfill --table=survey --tenantId=default --batchSize=500 --dryRun=true --legacyDigits=true --server.port=0 --spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://127.0.0.1:1/jwks"
```

Real backfill requires explicit operator approval:

```powershell
mvn -q -DskipTests spring-boot:run "-Dspring-boot.run.arguments=phone-privacy-backfill --table=survey --tenantId=default --batchSize=500 --dryRun=false --legacyDigits=true --server.port=0 --spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://127.0.0.1:1/jwks"
```

If existing `phone_hash` values were written with a lost or replaced key while
legacy `survey.phone` is still present, use the explicit rekey mode. Dry run
first; real rekey requires `--confirmRekey=true` and rewrites only derived
phone privacy columns:

```powershell
mvn -q -DskipTests spring-boot:run "-Dspring-boot.run.arguments=phone-privacy-backfill --table=survey --tenantId=default --batchSize=500 --dryRun=true --legacyDigits=true --rekey=true --server.port=0 --spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://127.0.0.1:1/jwks"
mvn -q -DskipTests spring-boot:run "-Dspring-boot.run.arguments=phone-privacy-backfill --table=survey --tenantId=default --batchSize=500 --dryRun=false --legacyDigits=true --rekey=true --confirmRekey=true --server.port=0 --spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://127.0.0.1:1/jwks"
```

Run `V20260628_002_clear_legacy_plain_phone.sql` only after coverage shows
`need_backfill_rows=0` and the phone/suffix/keyword search checks pass.

This project does not currently use Flyway, Liquibase, or another automated
database migration runner.

Use this directory for manually reviewed, versioned MySQL 8 migration files.
Run them once, in filename order, after a production backup and a test-database
rehearsal. Do not run root bootstrap dumps such as `xyyx_db.sql` against
production because they contain destructive initialization logic.

On Windows, run the MySQL client with `--default-character-set=utf8mb4`.
Without it, Chinese defaults such as `回访任务` can fail during DDL parsing.
