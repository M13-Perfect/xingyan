# Current Tasks

Last updated: 2026-06-25 after survey social-account field.

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
   - 2026-06-25 auth entry: unauthenticated users stay on business `/login`; protected routes rewrite to `/login?returnTo=<internal path>` and no longer navigate the main browser page to Casdoor HTML. `src/App.vue` posts the local login form to Casdoor `/api/login` with the existing OIDC code context, then exchanges the returned authorization code. `src/auth/oidc.js` keeps PKCE S256 and now stores `state`, `nonce`, `codeVerifier`, `codeChallenge`, sanitized `returnTo`, `createdAt`, and one-shot `consumed` in `sessionStorage`.
   - 2026-06-25 Headless Login 401 fix: Casdoor expects `code_challenge_method` and `code_challenge` in the `/api/login` query context. The frontend previously sent `challengeMethod` and `codeChallenge`, causing the login/token path to fail and surface as 401. The regression test now asserts snake_case keys are present and camelCase keys are absent.
   - 2026-06-25 login/app UI simplification: the business `/login` view is now a centered, low-noise form with labels, password visibility toggle, and a copyright footer. It deliberately does not preview customer data or customer lists before authentication. The authenticated workbench was restyled to the same quiet light theme without changing API/auth behavior.
   - Real user acceptance completed: local Casdoor organization `xyyx`, application `xyyx`/client ID `xyyx-web`, and user `18007300157` were configured. Clean-browser login entered the system, and `/api/me` accepted the Casdoor access token.
   - Token compatibility fix: Casdoor access tokens from this runtime include `name` but not `preferred_username`; `CurrentUserService` now prefers `preferred_username` and falls back to `name` before looking up local `user.role`.
   - Latest frontend verification: `npm test` 5/5 and `npm run build` passed. Runtime source check excluding regression tests found no `grant_type=password`, no `client_secret`, no `login/oauth/authorize`, no `Math.random`, no camelCase Casdoor PKCE keys, and no old "only responsible customers" login text in `xyyx-frontend/src`. Browser visual QA for login desktop/mobile, password visibility, mocked authenticated workbench, and the 2026-06-25 responsive topbar fix used local mocked `/api` responses for layout only; do not treat those screenshots as real backend or Casdoor acceptance.

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
- Some original project files had encoding display issues in PowerShell; verify encoding in an editor before bulk text rewrites.

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
