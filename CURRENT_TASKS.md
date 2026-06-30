# Current Tasks

Last updated: 2026-06-30 after next follow-up date UI/API repair.

## Current Goal

Continue iterating the active `xyyx` survey/customer-follow-up system. The latest remote code moved the active frontend into `xyyx/xyyx-frontend/` and removed the old `XinghanApp/` tree.

## P0 Decisions

1. SSO provider:
   - Selected plan: self-hosted Casdoor as the OAuth2/OIDC SSO center.
   - Acceptance artifact: `scripts/acceptance/Verify-CasdoorSso.ps1`.
   - Chinese acceptance notes: `docs/acceptance/casdoor-sso-acceptance.md`.
   - Deployment notes: `docs/deployment/casdoor-sso.md`.
   - Implementation status: backend Resource Server + `/api/me` and frontend Authorization Code + PKCE are implemented locally.
   - Runtime status: `deploy/casdoor/` starts a local Casdoor container on port `8000` with SQLite. On 2026-06-22, LAN discovery and JWKS were verified at `http://192.168.1.29:8000`; the no-token acceptance baseline passed 16/16.
   - 2026-06-29 local runtime status: current LAN IP is still `192.168.1.29`; Docker/Casdoor, Spring Boot, and Vite were started and verified. Checks: Casdoor discovery/JWKS `200`, backend `/api/security/public-key` `200`, backend `/api/me` expected pre-login `401`, frontend `/` `200`, and Vite proxied `/api/me` expected `401`.
   - 2026-06-30 local restart wrapper: root `restart-xingyan.bat` starts Casdoor, stops existing `8080`/`5173` listeners, and launches backend/frontend with matching `http://192.168.1.29` Casdoor/frontend URLs. Use `restart-xingyan.bat <LAN_IP>` if the LAN IP changed.
   - 2026-06-25 auth entry: unauthenticated users stay on business `/login`; protected routes rewrite to `/login?returnTo=<internal path>` and no longer navigate the main browser page to Casdoor HTML. `src/App.vue` posts the local login form to Casdoor `/api/login` with the existing OIDC code context, then exchanges the returned authorization code. `src/auth/oidc.js` keeps PKCE S256 and now stores `state`, `nonce`, `codeVerifier`, `codeChallenge`, sanitized `returnTo`, `createdAt`, and one-shot `consumed` in `sessionStorage`.
   - 2026-06-25 Headless Login 401 fix: Casdoor expects `code_challenge_method` and `code_challenge` in the `/api/login` query context. The frontend previously sent `challengeMethod` and `codeChallenge`, causing the login/token path to fail and surface as 401. The regression test now asserts snake_case keys are present and camelCase keys are absent.
   - 2026-06-25 login/app UI simplification: the business `/login` view is now a centered, low-noise form with labels, password visibility toggle, and a copyright footer. It deliberately does not preview customer data or customer lists before authentication. The authenticated workbench was restyled to the same quiet light theme without changing API/auth behavior.
   - Real user acceptance completed: local Casdoor organization `xyyx`, application `xyyx`/client ID `xyyx-web`, and user `18007300157` were configured. Clean-browser login entered the system, and `/api/me` accepted the Casdoor access token.
   - Token compatibility fix: Casdoor access tokens from this runtime include `name` but not `preferred_username`; `CurrentUserService` now prefers `preferred_username` and falls back to `name` before looking up local `user.role`.
   - Latest frontend verification: `npm test` 5/5 and `npm run build` passed. Runtime source check excluding regression tests found no `grant_type=password`, no `client_secret`, no `login/oauth/authorize`, no `Math.random`, no camelCase Casdoor PKCE keys, and no old "only responsible customers" login text in `xyyx-frontend/src`. Browser visual QA for login desktop/mobile, password visibility, mocked authenticated workbench, and the 2026-06-25 responsive topbar fix used local mocked `/api` responses for layout only; do not treat those screenshots as real backend or Casdoor acceptance.
   - 2026-06-26 workbench create-record UI: the always-visible survey create form in `src/App.vue` was replaced by one `+ 录入新回访记录` button that opens the same fields in a modal. Verified with frontend `npm test`, `npm run build`, and in-app Browser layout QA using temporary local Casdoor/API mocks only.
   - 2026-06-28 live domain `Network Error` fix: the deployed frontend bundle uses `https://sso.xujie.xin`, `https://xujie.xin/auth/callback`, and `xyyx-web`; the live blocker was infra, not PKCE. Fixed `xujie.xin` / `www.xujie.xin` certificate binding, restored Casdoor `/conf/app.conf` file after Docker had an empty directory at the mount source, fixed `/www/wwwroot/casdoor/logs` ownership for container UID `1000`, and restarted `casdoor`. Verification: `https://xujie.xin/api/me` returns expected pre-login `401`, and SSO discovery/get-app-login return `200` with CORS. Follow-up `user doesn't exist` was production identity data: Casdoor MySQL had the `xyyx` organization and `xyyx-web` app but no `xyyx/*` users, while the business DB had `18007300157` as `admin` with a BCrypt hash. `xyyx/18007300157` was inserted into Casdoor using the existing BCrypt hash, with no plaintext password exposure; a wrong-password probe now returns `password or code is incorrect` instead of `user doesn't exist`, and the probe failure counter was reset to 0. A later post-credential `Network Error` came from duplicated CORS on `POST /api/login/oauth/access_token`: upstream Casdoor emitted an empty `Access-Control-Allow-Origin` and Nginx added `https://xujie.xin`. Production SSO Nginx now hides upstream CORS headers and emits one origin; invalid-code token POST returns HTTP `400 invalid_grant` with single `Access-Control-Allow-Origin: https://xujie.xin`. Updated artifacts: `release/xyyx-frontend-dist.zip`, `release/xyyx-nginx-full-conf-20260628-tlsfix.zip`, `release/apply-xujie-nginx-tlsfix.sh`, and `release/server-issue-xujie-cert.py`.

2. Authorization boundary:
   - Current API derives user identity from Casdoor JWT claim `preferred_username`.
   - Current API derives role from local MySQL `user.role`.
   - Do not reintroduce authorization decisions based on client-supplied `username`, `role`, `operatorUsername`, or `owner`.

3. Survey deletion model:
   - `SurveyMapper.deleteById` still performs physical delete.
   - Project rule prefers logical delete. Add `is_deleted/deleted_at` to `survey` before exposing destructive workflows.

4. Survey sharing model:
   - Current `shared_users` comma-separated string plus `FIND_IN_SET` is not scalable.
   - Normalize to a `survey_share` table when access-control rules become important.

5. Survey social-account field:
   - Implemented optional `socialAccount` end to end: Vue create/list/detail UI, `SurveyController.addSurvey`, `SurveyMapper` insert/search, `Survey.socialAccount`, and `xyyx_db.sql` `survey.social_account`.
   - Existing databases need `ALTER TABLE survey ADD COLUMN social_account varchar(100) DEFAULT NULL AFTER wechat;` before running the new backend.
   - Verified on 2026-06-25 with `mvn test`, frontend `npm test`, `npm run build`, and an in-app browser check that the detail modal shows `社交账号` directly below `城市`.

6. Sales-workflow database compatibility, 2026-06-26:
   - Added manual versioned migrations under `db/migrations/` because the project has no Flyway/Liquibase migration runner.
   - `V20260626_001_expand_sales_workflow.sql` is additive-only: it adds compatibility columns/indexes and new workflow tables, with no DROP/RENAME and no first-round foreign keys.
   - `V20260626_002_backfill_sales_workflow_compat.sql` backfills owner/user IDs and initializes `user_role` from legacy `user.role` values without duplicate active rows.
   - Verification SQL lives at `db/verify/V20260626_verify_sales_workflow.sql`; operator docs live under `docs/database/`.
   - `SurveyMapper` no longer uses `SELECT *` for survey list queries; new test `SurveyMapperCompatibilityTests` enforces explicit columns.
   - `xyyx_db.sql` now includes the same sales-workflow schema for local full bootstrap, while existing DB upgrades should still use the additive migration files.
   - Local MySQL `xyyx_db` was upgraded and verified on 2026-06-26: 9 new workflow tables exist, `sys_role` has 5 roles, and owner/user state backfill completed for the current local rows.
   - 2026-06-26 employee create fix: `SurveyController.addUser` now transactionally inserts both `user` and active `user_role` for `STAFF`; staff delete revokes active `user_role` rows before deleting the legacy user. Verified with `mvn test`, frontend `npm test`, `npm run build`, and non-destructive in-app Browser smoke check. No live test employee was submitted.
   - 2026-06-28 VPS data import: `xyyx_db_2026-06-27_12-37-40_mysql_data_nxNKg.sql` was imported into the production business DB as data-only. The dump's `DROP/CREATE` statements were not executed; `survey` and `user` inserts were converted to `INSERT IGNORE` with explicit legacy column lists. Schema hash stayed unchanged. Counts changed `survey: 0 -> 275`, `user: 1 -> 13`. VPS backups: `/www/backup/xyyx-db/xyyx_db-before-data-import-20260628203524.sql` and `/www/backup/xyyx-db/xyyx_db-after-data-import-20260628203641.sql.gz`.

7. Phone privacy module, 2026-06-28:
   - Implemented on the real customer table `survey` because no separate `customer` table exists.
   - Backend uses libphonenumber normalization to E.164, JDK AES-256-GCM with 12-byte random IV and 16-byte tag, HMAC-SHA256 indexed hashes, `phoneMask`, and `personal_info_access_log`.
   - `POST /api/surveys` now writes legacy `phone` as NULL and stores encrypted/indexed phone fields. `GET /api/surveys` no longer selects or searches plaintext `phone`; exact and suffix4 phone lookup use POST endpoints under `/api/customers/search/*`.
   - `POST /api/customers/{customerId}/phone/reveal` requires logged-in user, row access, `PHONE_VIEW_FULL` permission via `role_permission`, non-empty reason, and writes audit rows for success and denied attempts.
   - Frontend list/detail show `phoneMask`, phone searches are POST body calls, and reveal results live only in an in-memory `Map` with 60-second expiry.
   - Migrations are manual: run `V20260628_001_add_phone_privacy.sql`, backfill existing plaintext `survey.phone` into encrypted fields with production env keys, then run `V20260628_002_clear_legacy_plain_phone.sql`. Do not run V002 before backfill verification.
   - Verified with backend `mvn test` (16 tests), frontend `npm test` (7 tests), and `npm run build`.
   - 2026-06-29 search repair: `GET /api/surveys?keyword=` is again the only回访列表 search endpoint. The backend classifies phone-shaped keywords inside `SurveyController`: valid full phone -> `phone_hash`, 4 digits -> `phone_suffix4_hash`, ordinary text -> existing non-phone keyword fields, and legacy digit-like phone input -> the same deterministic legacy hash used by backfill. The frontend removed the回访页 `/api/customers/search/phone*` calls and now catches `fetchData()` errors. Added `db/verify/V20260628_preflight_phone_privacy.sql` for missing-column preflight, `db/verify/V20260628_verify_phone_privacy.sql` for post-V001 coverage, and `PhonePrivacyBackfillTask` for explicit backfill runs. Local DB status after approval: `V20260628_001_add_phone_privacy.sql` has been executed on `xyyx_db`, all 12 phone privacy columns are present, and strict dryRun originally reported 52 `PHONE_INVALID` rows. The opt-in compatibility path `--legacyDigits=true` was added for historical rows only; initial local backfill completed with `scanned=53`, `updated=53`, `invalid=0`, `legacy=52`, `dryRun=false`, and coverage is `total_rows=53`, `legacy_phone_rows=53`, `hashed_rows=53`, `masked_rows=53`, `encrypted_rows=53`, `need_backfill_rows=0`. Fresh verification then found the User-scope `XYYX_PHONE_*` env vars were missing, so new formal local User-scope keys were generated without printing values. Those current keys do not match the already backfilled hashes (`exact_hash_match_rows=0`, `suffix4_hash_match_rows=0`), so phone search acceptance is still blocked until the existing privacy columns are rekeyed/re-encrypted from legacy `survey.phone`. V002 has not been executed.

Latest phone privacy status, supersedes older 2026-06-29 key-mismatch notes: ordinary User-scope `XYYX_PHONE_*` keys are set. A second controlled local rekey on 2026-06-30 repaired single reveal `PHONE_DECRYPT_FAILED/AEAD_TAG_MISMATCH` for `surveyId=8`: `phone-privacy-backfill --table=survey --tenantId=default --batchSize=500 --dryRun=false --legacyDigits=true --rekey=true --confirmRekey=true` completed with `scanned=53`, `updated=53`, `invalid=0`, `skipped=0`, `legacy=52`. Post-write offline decrypt for `surveyId=8` succeeds, and MockMvc `POST /api/surveys/8/phone/reveal` returns HTTP 200 with an in-memory 60-second reveal result. One residual row (`id=4`) lacks `phone_suffix4_hash` because its legacy phone length is 1. `V20260628_002_clear_legacy_plain_phone.sql` has not been executed.

Phone reveal session implementation, 2026-06-30: `db/migrations/V20260629_001_phone_reveal_session.sql` and `db/verify/V20260629_verify_phone_reveal_session.sql` cover reveal policy/session schema, audit fields, permissions, indexes, default policy, and aggregate privacy coverage without selecting plaintext phone. The backend requires a real JWT `sid`, `sessionId`, or `jti`; if Casdoor does not emit one, session-wide full-phone display is denied by design. Survey lists decrypt only the current returned page under an active login-session reveal, and rows with missing privacy columns stay masked instead of causing a page-level 500. Policy/session table absence now falls back to disabled masked mode for ordinary list/status/disable paths, preventing migration-order 500s while still requiring the migration before the feature can be enabled. The frontend topbar control is an eye icon button; full numbers live only in memory and are cleared/downgraded on 401, logout, disable, or session expiry, with explicit 403/409/429/500 toggle messages. `SurveyMapper` ordinary backfill reprocesses partial rows when any required privacy field is NULL or blank. Operator notes: `docs/database/phone-privacy-operator.md`. DB write commands were not run without approved credentials.

Single phone reveal repair, 2026-06-30: focus future debugging on `POST /api/surveys/{surveyId}/phone/reveal`; do not re-open login or ordinary list migration unless symptoms change. Frontend request path/body/token/prompt/single-flight checks pass; it now displays backend JSON `message/code/requestId` instead of only HTTP status. Backend reveal checks privacy field completeness and IV/tag length before decrypt and returns `409 PHONE_PRIVACY_NOT_READY` for incomplete old rows. Unknown encryption key version, AES-GCM tag mismatch, and cipher format errors are logged by requestId without secrets and return `500 PHONE_DECRYPT_FAILED`. Missing audit table/columns now returns `AUDIT_LOG_UNAVAILABLE` instead of an unclassified 500. The V20260628 verify/V002 SQL now require full privacy coverage before treating legacy phone rows as safe to clear.

Single phone reveal AEAD repair result, 2026-06-30: requestId `d8df0ab6-3cbf-454c-9633-cdc0a94c4601` was traced in `runtime-logs/backend-20260630-133216.out.log` to `surveyId=8` and `AEAD_TAG_MISMATCH`. Field checks showed complete ciphertext/IV/tag/key-version structure and loaded `v1` keys, so the failure was stale local ciphertext under a different `v1` key value. Before rekey, a privacy-field rollback backup was saved at `.docker-tmp/phone-privacy-backups/survey-phone-privacy-before-rekey-20260630-134615.sql` with SHA-256 `608e68c1eb1846fbe846bbf7dbffd504e093b26fdc847be0350acf288b33c1ea`. Do not print or commit this backup.

Global phone display/system settings, 2026-06-30: corrected the previous staff-level phone policy direction into one tenant/global setting. New manual migration/verify files are `db/migrations/V20260630_001_tenant_system_settings.sql` and `db/verify/V20260630_verify_tenant_system_settings.sql`; they have not been executed against a live DB in this task. Admin APIs are now `GET/PUT /api/admin/system-settings`, and `GET /api/app-settings` returns non-sensitive defaults such as `orderPageSize`. `GET /api/users` no longer returns phone policy fields. `GET /api/surveys` applies global `CLICK_TO_SESSION_VISIBLE` or `MASKED_ONLY`; a successful `POST /api/surveys/{surveyId}/phone/reveal` first enforces row visibility, then activates current user + current login session in `phone_reveal_login_session`. Full phone values are kept only in frontend memory, cleared on refresh/logout/401, and never stored in URL/sessionStorage/localStorage/console.

Phone privacy ON/OFF toggle, 2026-06-30: the 系统设置 modal's "手机号策略" row was renamed "手机号隐私设置" and its dropdown replaced by a slide switch (ON = slide-left/green, OFF = slide-right/red). ON = existing `CLICK_TO_SESSION_VISIBLE` (click any visible order's eye → whole login session unmasked). OFF = new `GlobalPhoneDisplayPolicyMode.SINGLE_ORDER_TIMED_REVEAL`: clicking the eye reveals ONLY that order for 300s, server-enforced via new table `phone_reveal_order_grant` (V20260630_002, additive/idempotent). `MASKED_ONLY` ("仅脱敏") was removed from the UI (enum value kept @Deprecated for legacy DB rows). Reveal endpoint branches on policy: OFF calls `PhoneRevealSessionService.activateOrder` (300s grant, no session-wide activation) and returns `phoneRevealStatus=SINGLE_ORDER_REVEALED` + `expiresInSeconds=300` + `phoneRevealExpiresAt`; the list (`SurveyPhoneDisplayService.revealRows`) decrypts only orders with an active grant and stamps each row's `phoneRevealExpiresAt`, the rest stay masked + clickable. Switching policy calls `invalidateForTenant` which now also disables order grants. Visibility-first ordering unchanged: row access is enforced before any reveal. Frontend keeps full numbers in memory only, auto-remasking at the per-row expiry via the existing sweep. Verified: backend `mvn test` (incl. new `singleOrderTimedRevealsOnlyGrantedOrders` / `...NoGrant...` reflection tests), frontend `npm test` 20/20. Local `xyyx_db` has V20260630_002 applied + verified. Follow-up fix: V20260630_001 created a CHECK constraint `chk_tenant_system_phone_policy` allowing only the old two values, so saving OFF (`SINGLE_ORDER_TIMED_REVEAL`) failed with 500 (CHECK violation). Corrective migration `db/migrations/V20260630_003_fix_phone_policy_check.sql` drops+re-adds the constraint with all three values (idempotent); V20260630_001 and the verify SQL were also updated for fresh installs. Applied to local `xyyx_db`; an OFF write now succeeds. No backend restart needed for this DB-only fix.

System settings / phone reveal migration applied locally, 2026-06-30: the two runtime errors (`保存设置 500 INTERNAL_SERVER_ERROR` and 小眼睛 `PHONE_SESSION_UNAVAILABLE`) were NOT code bugs — they were missing migration tables. Live `xyyx_db` had V20260628 applied but V20260629 and V20260630 unrun, so `tenant_system_settings` (save→500) and `phone_reveal_login_session` plus the `personal_info_access_log` audit-extension columns (reveal→`PHONE_SESSION_UNAVAILABLE`, and would have caused `AUDIT_LOG_UNAVAILABLE` on save) were absent. Fix: ran `db/migrations/V20260629_001_phone_reveal_session.sql` then `V20260630_001_tenant_system_settings.sql` against local `xyyx_db` (both additive/idempotent, `CREATE TABLE IF NOT EXISTS` + stored-proc column guards, no DROP/RENAME). `db/verify/V20260629_*` and `V20260630_*` pass; default settings row is `default / CLICK_TO_SESSION_VISIBLE / 20`. Backend uses JdbcTemplate (no schema cache) so the fix is live without restart. No data rows were modified; rollback is `DROP TABLE` on the two new tables if ever needed.

Next follow-up date repair, 2026-06-30: the former “设置下次提醒” control now matches its real behavior: it updates `survey.next_survey_date`; it does not create a notice, push, timer, or notification job. Backend `PUT /api/surveys/{id}/date` now uses the JWT current user, validates required/valid/non-past date input, updates via admin/staff row scope with tenant guard, and returns JSON for the frontend. Frontend detail modal shows `下次回访日期`, a note that the list will mark `需复访` and no system notification is sent, plus saving/success/error state. Verified with `mvn test`, frontend `npm test`, and `npm run build`; in-app Browser detail-flow QA was blocked by local mock/runtime constraints.

## P1 Refactor Backlog

- Add Service layer for survey and user operations.
- Add DTO/request/response objects instead of raw `Map<String, Object>`.
- Add validation for survey/user/notice payloads.
- Restrict CORS to known frontend origins before deployment.
- Replace leading wildcard search with a measured strategy: generated columns, full-text index, or search table depending on real query needs.
- Review RSA key lifecycle. Current backend-generated keypair is in-memory and rotates on restart.
- Add tests around login, staff CRUD, survey visibility, and notice read/delete state.

## Redis Position

No Redis cache layer exists now. Do not add Redis yet. Current priority is authorization, data model cleanup, and service boundaries. Add Redis only after query hotspots are measured and the API contract is stable.

## Known Risks

- `admin.sql` contains local credential seed data. Do not quote values in responses.
- `xyyx/src/main/resources/application.properties` is intentionally ignored and should not be read unless explicitly needed.
- Real Casdoor browser SSO token-backed `/api/me` was verified on 2026-06-22 with the temporary local test user.
- LAN IP can change. Always re-read `/.well-known/openid-configuration` and use the actual `issuer` / `jwks_uri`.
- The local Casdoor `xyyx` organization password complexity was relaxed to allow a temporary short password for testing. Restore stronger password policy before production.
- If a Casdoor app requires `client_secret`, do not place it in Vue; switch to a backend authorization-code exchange endpoint.
- Casdoor MFA/consent/prompt or account-update-required states are intentionally not rendered in the business SPA; the current client stops on `/login` with an admin-handling message instead of navigating to Casdoor HTML.
- Survey search and sharing are not index-friendly at scale.
- Existing production rows imported before the phone privacy module may still have plaintext `survey.phone` until a key-backed backfill and `V20260628_002_clear_legacy_plain_phone.sql` are run. New application writes do not store plaintext phone.
- Historical 2026-06-29 key mismatch was repaired locally by controlled rekey/re-encrypt from legacy `survey.phone`. Do not run `V20260628_002_clear_legacy_plain_phone.sql` yet; legacy phone is still the local recovery source, and row `id=4` has a known one-character legacy phone that cannot produce suffix4 hash coverage.
- `tenant_system_settings` migration was applied to the LOCAL `xyyx_db` on 2026-06-30 (see handoff note below). For any OTHER environment it is still pending: apply `V20260629_001_phone_reveal_session.sql`, then `V20260630_001_tenant_system_settings.sql`, then `V20260630_002_phone_reveal_order_grant.sql` (backs the OFF / single-order 300s reveal), then `V20260630_003_fix_phone_policy_check.sql` (lets the policy CHECK accept the OFF value) after backup, and run the matching `db/verify/*.sql` before relying on the admin system settings UI / phone reveal.
- New workflow tables are schema-ready only. Backend double-write/read-switch work is intentionally deferred in `docs/database/sales-workflow-backend-todo.md`.
- Some original project files had encoding display issues in PowerShell; verify encoding in an editor before bulk text rewrites.

Phone privacy local state update: local V001, initial backfill, and controlled rekey have completed. Current key decrypts `surveyId=8` after the 2026-06-30 AEAD repair. Do not rotate User-scope `XYYX_PHONE_*` keys or run V002 before final API/search acceptance and explicit handling of the one-character residual row.

## Minimal Next-Step Files

For backend API work:

- `xyyx/src/main/java/org/example/xyyx/controller/SurveyController.java`
- `xyyx/src/main/java/org/example/xyyx/controller/NoticeController.java`
- `xyyx/src/main/java/org/example/xyyx/service/NoticeService.java`
- `xyyx/src/main/java/org/example/xyyx/mapper/SurveyMapper.java`

For frontend API/UI work:

- `xyyx/xyyx-frontend/src/App.vue`
- `xyyx/xyyx-frontend/vite.config.js`
- `xyyx/xyyx-frontend/package.json`

For Casdoor runtime/deployment work:

- `deploy/casdoor/README.md`
- `deploy/casdoor/docker-compose.yml`
- `deploy/casdoor/conf/app.conf`
- `docs/deployment/casdoor-sso.md`
- `scripts/acceptance/Verify-CasdoorSso.ps1`

For DB work:

- `xyyx_db.sql`
- `admin.sql`
- `db/migrations/`
- `db/verify/`
- `docs/database/`
