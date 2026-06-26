# Project Index

Last verified: 2026-06-25 after survey social-account field.

SSO update: 2026-06-25 frontend keeps Authorization Code + PKCE S256 but no longer navigates the main browser page to Casdoor HTML. Unauthenticated users land on business `/login`; protected paths rewrite to `/login?returnTo=<internal path>`. The SPA posts the business login form to Casdoor `/api/login`, receives an authorization code, exchanges it without a client secret or password grant, and stores API tokens in `sessionStorage`. Headless Login must send Casdoor-compatible PKCE query keys `code_challenge_method=S256` and `code_challenge=<value>`; do not use `challengeMethod` or `codeChallenge` for Casdoor API calls.

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
- Survey/customer records include optional `socialAccount` mapped to `survey.social_account`; `POST /api/surveys` accepts it and keyword search includes `social_account`.
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

- `xyyx_db.sql`
- `admin.sql`

Tables observed:

- `user`
- `survey`
- `notice`
- `notice_user_state`

Important constraints and indexes observed:

- `user.username` unique index.
- `survey.phone` unique index.
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
- RSA-OAEP password encryption is still used for staff create/password reset payloads.
- Survey/customer follow-up list, create, process, date, share, remarks, delete. `socialAccount` is shown in the create form, list contact block, and under `城市` in the detail modal.
- Staff management with create/delete/password update.
- Notification center with unread/read/all filters, batch read/delete, and admin publish.

## Run Notes

Casdoor:

```powershell
cd C:\Users\Administrator\Desktop\xingyan\deploy\casdoor
docker compose up -d
```

For phone testing, use the current LAN discovery values, not `localhost`.

Backend:

```powershell
cd C:\Users\Administrator\Desktop\xingyan\xyyx
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.security.oauth2.resourceserver.jwt.issuer-uri=http://<LAN_IP>:8000 --spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://<LAN_IP>:8000/.well-known/jwks"
```

Frontend:

```powershell
cd C:\Users\Administrator\Desktop\xingyan\xyyx\xyyx-frontend
npm install
$env:VITE_CASDOOR_BASE_URL="http://<LAN_IP>:8000"
$env:VITE_CASDOOR_REDIRECT_URI="http://<LAN_IP>:5173/auth/callback"
$env:VITE_CASDOOR_CLIENT_ID="xyyx-web"
npm run dev -- --host 0.0.0.0 --strictPort
```

Database:

```sql
SOURCE C:/Users/Administrator/Desktop/xingyan/xyyx_db.sql;
SOURCE C:/Users/Administrator/Desktop/xingyan/admin.sql;
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
