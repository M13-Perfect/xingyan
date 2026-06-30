# Project Index

Last verified: 2026-06-30 after next follow-up date UI/API repair.

SSO update: 2026-06-25 frontend keeps Authorization Code + PKCE S256 but no longer navigates the main browser page to Casdoor HTML. Unauthenticated users land on business `/login`; protected paths rewrite to `/login?returnTo=<internal path>`. The SPA posts the business login form to Casdoor `/api/login`, receives an authorization code, exchanges it without a client secret or password grant, and stores API tokens in `sessionStorage`. Headless Login must send Casdoor-compatible PKCE query keys `code_challenge_method=S256` and `code_challenge=<value>`; do not use `challengeMethod` or `codeChallenge` for Casdoor API calls.

Deployment update: 2026-06-28 live `Network Error` fix completed on `8.156.93.144`. Root causes were: `xujie.xin:443` served a cert whose SAN was only `DNS:www.xujie.xin`, `www.xujie.xin:443` served the `sso.xujie.xin` cert, then Casdoor was down because Docker saw `/www/wwwroot/casdoor/conf/app.conf` as a directory and `/www/wwwroot/casdoor/logs` was not writable by container UID `1000`. Fixed by issuing a Let's Encrypt cert for `xujie.xin` + `www.xujie.xin`, updating/reloading Nginx, restoring `app.conf` from `/www/wwwroot/xyyx-source/conf/app.conf`, chowning Casdoor logs to `1000:1000`, and restarting the `casdoor` container. External checks now show `https://xujie.xin/api/me` reaches backend with expected pre-login `401`, and SSO discovery/get-app-login return `200` with CORS. The follow-up `user doesn't exist` login bug was production identity data, not frontend code: Casdoor had organization `xyyx` and application `xyyx-web`, but only `built-in/admin` existed in its MySQL user table. The production business DB had `18007300157` with role `admin` and a BCrypt hash, so `xyyx/18007300157` was inserted into Casdoor with the same BCrypt hash, no plaintext password exposure. A wrong-password probe now returns `password or code is incorrect`, proving the flow reaches password verification instead of user lookup failure. The later post-credential `Network Error` was token-exchange CORS: Casdoor upstream returned an empty `Access-Control-Allow-Origin` and Nginx appended `https://xujie.xin`, creating duplicate/invalid CORS. Production `sso.xujie.xin.conf` now hides upstream CORS headers and emits exactly one allowed origin; invalid-code token POST returns HTTP `400` with single `Access-Control-Allow-Origin: https://xujie.xin`. Updated artifacts: `release/xyyx-frontend-dist.zip`, `release/xyyx-nginx-full-conf-20260628-tlsfix.zip`, `release/apply-xujie-nginx-tlsfix.sh`, and `release/server-issue-xujie-cert.py`.

Data import update: 2026-06-28 imported `C:/Users/Administrator/Desktop/xyyx_db_2026-06-27_12-37-40_mysql_data_nxNKg.sql` into the VPS business MySQL database without schema changes. The dump contained data for `survey` and `user` plus old DDL; DDL was filtered out, `INSERT` was converted to `INSERT IGNORE`, and explicit legacy column lists were added because the VPS schema has newer compatibility columns. Verified schema hash unchanged before/after. Row counts changed `survey: 0 -> 275` and `user: 1 -> 13`. Backups on the VPS: pre-import plain SQL at `/www/backup/xyyx-db/xyyx_db-before-data-import-20260628203524.sql`, post-import compressed SQL at `/www/backup/xyyx-db/xyyx_db-after-data-import-20260628203641.sql.gz`. Temporary SQL files were removed from `/tmp` and local `C:/tmp`.

Phone privacy update: 2026-06-28 implemented the phone privacy module on the existing `survey` customer table. New writes normalize raw phone via libphonenumber, encrypt E.164 with AES-256-GCM, store HMAC-SHA256 `phone_hash` / `phone_suffix4_hash`, keep `phone_mask`, and write legacy `survey.phone` as NULL. Default list/search responses expose `phoneMask` only; exact and suffix4 phone searches use POST body endpoints, not URL query. The canonical single full-phone reveal path is `POST /api/surveys/{surveyId}/phone/reveal`, requires `PHONE_VIEW_FULL` in `role_permission`, row access, and writes `personal_info_access_log`; the frontend must not call old customer/search phone endpoints from the reveal button. Keys come only from env/private config: `XYYX_PHONE_ENC_KEYS`, `XYYX_PHONE_HASH_KEYS`, `XYYX_PHONE_ENC_KEY_VERSION`, `XYYX_PHONE_HASH_KEY_VERSION`. Manual migrations: `db/migrations/V20260628_001_add_phone_privacy.sql`, then backfill old rows with production keys, then `V20260628_002_clear_legacy_plain_phone.sql`.

Phone search repair update: 2026-06-29 `GET /api/surveys?keyword=` now recognizes phone-shaped keywords internally. Full valid phones are normalized and searched through `survey.tenant_id + survey.phone_hash`; 4-digit keywords search `survey.tenant_id + survey.phone_suffix4_hash`; ordinary keywords still search only non-phone fields. Legacy digit-like phone input uses the same `LEGACY_DIGITS_V1` deterministic hash as opt-in backfill; new survey writes still use strict libphonenumber validation only. The workbench no longer calls `/api/customers/search/phone*` for the回访列表 search box, and `fetchData()` now reports API failures instead of silently showing an empty list. A new `phone-privacy-backfill` `ApplicationRunner` can backfill existing `survey.phone IS NOT NULL AND phone_hash IS NULL` rows with `--table=survey --tenantId=<tenant> --batchSize=500 --dryRun=true|false --legacyDigits=true|false`; logs include survey IDs/error codes and aggregate counts only, never plaintext phone values, and the task now closes the Spring context after one-shot execution. V001 was executed on local `xyyx_db` after approval: all 12 phone privacy columns are present. Initial real `--dryRun=false --legacyDigits=true` backfill completed with `scanned=53`, `updated=53`, `invalid=0`, `legacy=52`, and coverage is `total_rows=53`, `legacy_phone_rows=53`, `hashed_rows=53`, `masked_rows=53`, `encrypted_rows=53`, `need_backfill_rows=0`. Fresh 2026-06-29 verification found the current User-scope `XYYX_PHONE_*` env vars were missing, so new formal local User-scope keys were generated without printing or committing values; those current keys do not match the already backfilled hashes (`exact_hash_match_rows=0`, `suffix4_hash_match_rows=0`). Search acceptance therefore still requires an approved rekey/re-encrypt of the existing privacy columns from legacy `survey.phone` before running V002. V002 is still not executed.

Phone search rekey status, supersedes older 2026-06-29 key-mismatch notes: local User-scope `XYYX_PHONE_*` keys are set in the ordinary app context. `phone-privacy-backfill --rekey=true --confirmRekey=true --dryRun=false --legacyDigits=true` completed on local `xyyx_db` with `scanned=53`, `updated=53`, `invalid=0`, `legacy=52`. Current coverage is `total_rows=53`, `legacy_phone_rows=53`, `hashed_rows=53`, `masked_rows=53`, `encrypted_rows=53`, `need_backfill_rows=0`, `suffix4_hashed_rows=52`, `legacy_version_rows=52`; current User key verification returns `exact_hash_match_rows=1`, `suffix4_hash_match_rows=1`. V002 is still not executed.

Phone reveal session implementation, 2026-06-30: added additive manual migration `db/migrations/V20260629_001_phone_reveal_session.sql` and verification SQL `db/verify/V20260629_verify_phone_reveal_session.sql` for login-session reveal state and audit extension fields. The backend prefers JWT `sid`, `sessionId`, or `jti`; if absent it derives a server-side token fingerprint from token/iat/exp without storing or logging the raw JWT. Survey lists decrypt full phone numbers only for the current returned page when the global phone policy is `CLICK_TO_SESSION_VISIBLE` and the current user/session is activated; partial or missing phone privacy columns degrade that row to masked display instead of failing the whole list. Ordinary backfill now treats any missing privacy field as incomplete (`phone_hash`, `phone_mask`, ciphertext, IV, tag, suffix4 hash). Operator notes: `docs/database/phone-privacy-operator.md`. DB write commands were not run without approved credentials.

Single reveal repair, 2026-06-30: the current bug scope is `POST /api/surveys/{surveyId}/phone/reveal`, not login and not ordinary `GET /api/surveys`. The frontend sends one empty-body request to the survey route with the axios bearer-token interceptor, no `window.prompt`, and single-flight cooldown. Backend reveal now rejects incomplete/invalid privacy structure before decrypt (`phone_ciphertext` non-empty, 12-byte IV, 16-byte tag, nonblank key version, tenant/customer UUID) with JSON `409 PHONE_PRIVACY_NOT_READY`; real decrypt failures return JSON `500 PHONE_DECRYPT_FAILED` with requestId and server log detail such as `KEY_VERSION_NOT_FOUND`, `AEAD_TAG_MISMATCH`, or `CIPHER_FORMAT_INVALID` without printing phone/key/ciphertext. Audit insert failure is explicit `AUDIT_LOG_UNAVAILABLE`. `db/verify/V20260628_verify_phone_privacy.sql` and `V20260628_002_clear_legacy_plain_phone.sql` now use full-field privacy coverage, not only `phone_hash`.

Single reveal AEAD local repair, 2026-06-30: manual click failure `requestId=d8df0ab6-3cbf-454c-9633-cdc0a94c4601` mapped to `surveyId=8` and backend detail `AEAD_TAG_MISMATCH`. Database fields were structurally complete (`v1`, 12-byte IV, 16-byte tag), and the app loaded `v1`; the local cause was stale ciphertext encrypted with a different `v1` key value. A rollback backup of privacy fields only was written to `.docker-tmp/phone-privacy-backups/survey-phone-privacy-before-rekey-20260630-134615.sql` before rekey. Controlled rekey from legacy `survey.phone` completed with `scanned=53 updated=53 invalid=0 skipped=0 legacy=52`. Offline decrypt for `surveyId=8` and MockMvc `POST /api/surveys/8/phone/reveal` now pass; V002 remains not executed.

Global phone display/system settings, 2026-06-30: the earlier per-employee phone policy implementation was corrected before its migration was run. Runtime no longer reads any employee policy table, and no longer exposes per-user or batch phone-policy admin APIs. New manual migration/verify files are `db/migrations/V20260630_001_tenant_system_settings.sql` and `db/verify/V20260630_verify_tenant_system_settings.sql`. Admins configure one global phone policy and default order page size through the topbar/mobile “设置” system settings modal. Default policy is `CLICK_TO_SESSION_VISIBLE`: employees initially see masks, then clicking any visible order eye activates full-phone display for the current user + current login session only. `MASKED_ONLY` returns masks and hides the eye button. Row visibility still decides which records a user can see, and full export still requires separate `PHONE_EXPORT_FULL` permission.

Next follow-up date repair, 2026-06-30: `设置下次提醒` was clarified as a `survey.next_survey_date` field, not a push/notification job. New survey creation still derives the initial date from `remindDays`; the list marks `未处理` rows as `需复访` when that date is due. `PUT /api/surveys/{id}/date` now requires the authenticated current user, rejects missing/invalid/past dates, updates only rows visible to that admin/staff user, and returns JSON `success/message/nextSurveyDate/due`. `xyyx/xyyx-frontend/src/App.vue` now shows `下次回访日期`, explains that no system notification is sent, and displays saving/success/error state. Tests: backend `SurveyReminderDateTests`, frontend `phonePrivacy.test.js`; `mvn test`, `npm test`, and `npm run build` passed. Browser detail-flow QA was attempted but blocked by local Browser/mock-server constraints.

## Read This First

This repository is now organized around one active product:

- `xyyx/`: Spring Boot 3.4.3 backend, Java 17, MyBatis annotation SQL, MySQL.
- `xyyx/xyyx-frontend/`: Vue 3 + Vite frontend, Axios, proxied `/api` calls.

`XinghanApp/` was removed by upstream commits and should no longer be treated as the active frontend.

## Current Verified Structure

```text
xingyan/
  admin.sql                 # local credential seed; do not quote secrets/hashes in responses
  xyyx_db.sql               # MySQL schema dump
  agents.md                 # project agent instructions and handoff notes
  PROJECT_INDEX.md          # this low-token structure index
  CURRENT_TASKS.md          # active backlog / next iteration notes
  db/migrations/            # manual versioned MySQL migrations; no runner wired
  db/verify/                # post-migration verification SQL
  docs/database/            # database migration notes and backend TODOs
  deploy/casdoor/           # local Casdoor Docker runtime; SQLite DB/logs ignored
  xyyx/
    pom.xml
    src/main/java/org/example/xyyx/
      config/
      controller/
      entity/
      mapper/
      service/
    src/main/resources/
      application-example.properties
    xyyx-frontend/
      package.json
      vite.config.js
      src/App.vue
      src/main.js
      src/style.css
```

Skip by default:

- `.git/`
- `.idea/`
- `xyyx/target/`
- `xyyx/xyyx-frontend/node_modules/`
- `xyyx/xyyx-frontend/dist/`
- `xyyx/src/main/resources/application.properties`

## Backend Truth

Entry point:

- `xyyx/src/main/java/org/example/xyyx/XyyxApplication.java`

Core files:

- `xyyx/src/main/java/org/example/xyyx/controller/SurveyController.java`
- `xyyx/src/main/java/org/example/xyyx/controller/AuthController.java`
- `xyyx/src/main/java/org/example/xyyx/controller/NoticeController.java`
- `xyyx/src/main/java/org/example/xyyx/mapper/SurveyMapper.java`
- `xyyx/src/main/java/org/example/xyyx/entity/Survey.java`
- `xyyx/src/main/java/org/example/xyyx/entity/Notice.java`
- `xyyx/src/main/java/org/example/xyyx/service/CryptoService.java`
- `xyyx/src/main/java/org/example/xyyx/service/CurrentUserService.java`
- `xyyx/src/main/java/org/example/xyyx/service/PhonePrivacyService.java`
- `xyyx/src/main/java/org/example/xyyx/service/SurveyPhoneDisplayService.java`
- `xyyx/src/main/java/org/example/xyyx/service/TenantSystemSettingsService.java`
- `xyyx/src/main/java/org/example/xyyx/service/PhoneRevealSessionService.java`
- `xyyx/src/main/java/org/example/xyyx/service/NoticeService.java`
- `xyyx/src/main/java/org/example/xyyx/config/SecurityConfig.java`
- `xyyx/src/main/java/org/example/xyyx/config/CorsConfig.java`

Current backend shape:

- Single Spring Boot app.
- MyBatis mapper still uses annotation SQL.
- `SurveyController` still contains controller + business/data-access logic.
- `NoticeService` exists for notice logic, but survey/user logic is not yet cleanly service-layered.
- Password flow now uses RSA-OAEP client encryption plus BCrypt password hashing.
- Casdoor Resource Server support is implemented for `/api/**`; `/api/me` derives identity from JWT `preferred_username` or Casdoor `name`, then derives local role from `user.role`.
- Local runtime can use Casdoor LAN issuer from discovery, for example `http://192.168.1.29:8000`; do not hard-code this IP without re-checking current discovery.
- No Redis cache layer.
- Backend controllers no longer use client-supplied `username` / `role` / `operatorUsername` as the authorization source; JWT + local DB role is the trust boundary.
- `POST /api/users` still creates only staff accounts, but now writes both legacy `user.role='staff'` and an active `user_role` row linked through `sys_role.code='STAFF'` in one transaction; `DELETE /api/users/{id}` revokes active `user_role` rows before deleting the legacy user.
- Survey/customer records include optional `socialAccount` mapped to `survey.social_account`; `POST /api/surveys` accepts it and keyword search includes `social_account`.
- Survey/customer phone is now privacy-protected on new writes. Do not read or return legacy `survey.phone`; use `phoneMask` for normal UI and reveal only through the audited endpoint.
- Phone display is resolved from tenant/global `TenantSystemSettingsService`, not from employee rows. `CLICK_TO_SESSION_VISIBLE` shows masks until the current user clicks a visible record eye, then `PhoneRevealSessionService` activates that user + login session and list responses decrypt only the current page. `MASKED_ONLY` returns masks and hides the eye button.
- CORS should still be reviewed before deployment.

Local Casdoor acceptance data:

- Organization: `xyyx`
- Application: `xyyx`
- Client ID: `xyyx-web`
- Redirect URI: `http://192.168.1.29:5173/auth/callback`
- Test user: `18007300157`
- Local-only caveat: the `xyyx` organization password complexity was relaxed for this temporary short-password account; restore stronger policy before production.

Current backend endpoints observed:

- `GET /api/security/public-key`
- `POST /api/login`
- `GET /api/me`
- `GET /api/users`
- `POST /api/users`
- `DELETE /api/users/{id}`
- `PUT /api/users/{id}/password`
- `GET /api/app-settings`
- `GET /api/admin/system-settings`
- `PUT /api/admin/system-settings`
- `GET /api/surveys/pending-count`
- `GET /api/surveys`
- `POST /api/surveys`
- `PUT /api/surveys/{id}/process`
- `DELETE /api/surveys/{id}`
- `PUT /api/surveys/{id}/date`
- `PUT /api/surveys/{id}/share`
- `PUT /api/surveys/{id}/remarks`
- `POST /api/notices`
- `GET /api/notices`
- `GET /api/notices/unread-count`
- `PUT /api/notices/read`
- `DELETE /api/notices`

## Database Truth

Schema files:

- `xyyx_db.sql` now includes the 2026-06-26 sales-workflow compatibility schema for local full bootstrap.
- `admin.sql`
- `db/migrations/V20260626_001_expand_sales_workflow.sql`
- `db/migrations/V20260626_002_backfill_sales_workflow_compat.sql`
- `db/migrations/V20260628_001_add_phone_privacy.sql`
- `db/migrations/V20260628_002_clear_legacy_plain_phone.sql`
- `db/migrations/V20260630_001_tenant_system_settings.sql`
- `db/verify/V20260626_verify_sales_workflow.sql`
- `db/verify/V20260630_verify_tenant_system_settings.sql`

Migration mechanism:

- No Flyway/Liquibase/Prisma/TypeORM/Alembic runner is configured.
- `db/migrations/` is manual and versioned. Run files once, in filename order, after backup and test rehearsal.
- `xyyx_db.sql` remains local bootstrap only; it contains `DROP TABLE IF EXISTS` and must not be used as a production migration.

Tables observed:

- `user`
- `survey`
- `notice`
- `notice_user_state`
- `tenant_system_settings`

Sales-workflow compatibility migration, 2026-06-26:

- Adds nullable/defaulted compatibility columns to `survey`, `notice`, and `notice_user_state`.
- Adds `sys_role`, `user_role`, `team`, `user_team`, `survey_share`, `followup_task`, `followup_record`, `followup_transfer`, and `operation_log` without foreign keys in round one.
- Backfills `survey.owner_user_id`, `survey.created_by_user_id`, `notice_user_state.user_id`, and active `user_role` rows for legacy `user.role` values `admin` / `staff`.
- `SurveyMapper` list queries now use explicit survey columns instead of `SELECT *`; `SurveyMapperCompatibilityTests` protects this.
- Local MySQL `xyyx_db` was upgraded on 2026-06-26 with the versioned migration files and verified with `db/verify/V20260626_verify_sales_workflow.sql`.

Important constraints and indexes observed:

- `user.username` unique index.
- Legacy `survey.phone` unique index still exists for compatibility, but application code no longer reads or writes plaintext phone. New searchable phone indexes are `tenant_id + phone_hash` and `tenant_id + phone_suffix4_hash`.
- `tenant_system_settings` has a unique `tenant_id` key, `tenant_id + enabled` index, and checks for `phone_display_policy` plus `order_page_size` range 1-100.
- `survey.wechat` unique index.
- `survey.social_account` is optional `varchar(100)` and intentionally has no unique index.
- `survey.status` secondary index.
- `notice.is_deleted + created_at` index.
- `notice.level + created_at` index.
- `notice_user_state.notice_id + username` unique key.
- `notice_user_state.username + is_deleted + is_read + notice_id` index.

Security note:

- `admin.sql` contains local credential seed data. Do not print values in responses.
- `user.password` is now sized for hashes and backend uses BCrypt, but bootstrap/rotation policy still needs review.

Performance note:

- Survey search still uses leading wildcard `LIKE '%keyword%'` across fields including `social_account`; normal B-tree indexes will not be used efficiently.
- Staff visibility still uses `FIND_IN_SET(shared_users)`, which is not index-friendly. Normalize sharing into a join table before scale.
- Survey delete is still physical delete in `SurveyMapper`; project rule prefers logical delete.
- Notice user state uses logical delete per user.

## Frontend Truth

Active frontend:

- `xyyx/xyyx-frontend/package.json`
- `xyyx/xyyx-frontend/vite.config.js`
- `xyyx/xyyx-frontend/src/App.vue`
- `xyyx/xyyx-frontend/src/main.js`
- `xyyx/xyyx-frontend/src/style.css`

Frontend stack:

- Vue 3.
- Vite.
- Axios.
- `const API = '/api'`.
- Vite proxy maps `/api` to the backend target.

Current frontend scope:

- Casdoor Authorization Code + PKCE S256 login, `/auth/callback` token exchange, `/api/me` profile fetch, and Axios Bearer token injection for `/api`. PKCE uses `crypto.subtle` when available and an internal SHA-256 fallback when only secure random values are available.
- Business-owned `/login` is the unauthenticated entry. `src/auth/oidc.js` stores 10-minute one-shot login transactions in `sessionStorage` with sanitized internal `returnTo`; `src/App.vue` rejects disabled auth-style routes such as `/signup`, `/forget`, `/consent`, `/prompt`, and `/account` instead of rendering or linking to them.
- Current `/login` UI is intentionally simple and data-free: centered form, explicit labels, password visibility toggle, copyright footer, no customer preview/list before authentication. The authenticated workbench follows the same quiet light visual style.
- Authenticated topbar is responsive in `src/App.vue`: desktop horizontal nav at `min-width: 901px`, mobile hamburger menu at `max-width: 900px`; there is no active JS device-width detector.
- Workbench survey creation is button-first: `src/App.vue` shows `+ 录入新回访记录` above the table and opens the create fields in a modal; the old always-visible inline create form should not be restored unless the workflow changes.
- RSA-OAEP password encryption is still used for staff create/password reset payloads.
- Survey/customer follow-up list, create, process, date, share, remarks, delete. `socialAccount` is shown in the create form, list contact block, and under `城市` in the detail modal.
- Staff management with create/delete/password update.
- Admins use the topbar/mobile “设置” entry to open a system settings modal with only global phone policy and default order page size. Staff management keeps only staff create/delete/password reset; it no longer contains phone policy columns or per-employee policy buttons.
- Notification center with unread/read/all filters, batch read/delete, and admin publish.

## Run Notes

2026-06-29 verified local LAN IP: `192.168.1.29`. Re-check it if the machine changes networks. If Docker daemon is down, start Docker Desktop before Casdoor.

Quick restart wrapper: run `restart-xingyan.bat` from the repo root. It starts Casdoor, stops existing `8080`/`5173` listeners, and launches backend/frontend with matching `http://192.168.1.29` URLs. Use `restart-xingyan.bat <LAN_IP>` if the LAN IP changed.

Casdoor:

```powershell
cd C:\Users\Administrator\Desktop\xingyan\deploy\casdoor
docker compose up -d
```

For phone testing, use the current LAN discovery values, not `localhost`.

Backend:

```powershell
cd C:\Users\Administrator\Desktop\xingyan\xyyx
$env:SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI="http://192.168.1.29:8000"
$env:SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI="http://192.168.1.29:8000/.well-known/jwks"
mvn spring-boot:run
```

Frontend:

```powershell
cd C:\Users\Administrator\Desktop\xingyan\xyyx\xyyx-frontend
$env:VITE_CASDOOR_BASE_URL="http://192.168.1.29:8000"
$env:VITE_CASDOOR_REDIRECT_URI="http://192.168.1.29:5173/auth/callback"
$env:VITE_CASDOOR_CLIENT_ID="xyyx-web"
npm run dev -- --host 0.0.0.0 --strictPort
```

Database:

```sql
SOURCE C:/Users/Administrator/Desktop/xingyan/xyyx_db.sql;
SOURCE C:/Users/Administrator/Desktop/xingyan/admin.sql;
```

To upgrade an existing local DB without dropping data, prefer:

```sql
SOURCE C:/Users/Administrator/Desktop/xingyan/db/migrations/V20260626_001_expand_sales_workflow.sql;
SOURCE C:/Users/Administrator/Desktop/xingyan/db/migrations/V20260626_002_backfill_sales_workflow_compat.sql;
SOURCE C:/Users/Administrator/Desktop/xingyan/db/verify/V20260626_verify_sales_workflow.sql;
```

Existing DBs created before 2026-06-25 need this one-time column add:

```sql
ALTER TABLE survey ADD COLUMN social_account varchar(100) DEFAULT NULL AFTER wechat;
```

Do not run seed SQL blindly against production.

## Token-Saving Rules For Next Iteration

Read in this order:

1. `PROJECT_INDEX.md`
2. `CURRENT_TASKS.md`
3. `agents.md`
4. Only the exact backend/frontend file tied to the task

For backend API work, start with:

- `xyyx/src/main/java/org/example/xyyx/controller/SurveyController.java`
- `xyyx/src/main/java/org/example/xyyx/controller/NoticeController.java`
- `xyyx/src/main/java/org/example/xyyx/mapper/SurveyMapper.java`

For Casdoor SSO acceptance work, start with:

- `deploy/casdoor/README.md`
- `deploy/casdoor/docker-compose.yml`
- `deploy/casdoor/conf/app.conf`
- `scripts/acceptance/Verify-CasdoorSso.ps1`
- `docs/acceptance/casdoor-sso-acceptance.md`
- `docs/deployment/casdoor-sso.md`

For frontend UI/API work, start with:

- `xyyx/xyyx-frontend/src/App.vue`
- `xyyx/xyyx-frontend/vite.config.js`

For DB work, start with:

- `xyyx_db.sql`
- `admin.sql`
