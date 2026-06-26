# Casdoor SSO 登录验收与使用说明

日期：2026-06-22

## 本次验收结果

- Casdoor 地址：`http://192.168.1.29:8000`
- 前端入口：`http://192.168.1.29:5173`
- 后端接口：`http://192.168.1.29:8080/api/me`
- Casdoor 组织：`xyyx`
- Casdoor 应用：`xyyx`
- Client ID：`xyyx-web`
- Redirect URI：`http://192.168.1.29:5173/auth/callback`
- 测试用户：`18007300157`

验收结论：

- clean-browser 从前端点击 `Casdoor SSO 登录` 后进入 Casdoor 登录页。
- 测试用户登录成功，并回到前端系统。
- 前端拿到 Casdoor access token 后请求 `/api/me` 成功。
- `/api/me` 返回 HTTP 200，用户名为 `18007300157`，业务角色来自本地 MySQL `user.role`。
- 自动验收脚本基础检查 16/16 通过，带真实 access token 检查 23/23 通过。

## 本次修复点

1. 前端 PKCE 修复：
   - 问题：部分浏览器/LAN 场景下 `crypto.subtle` 不可用，前端生成 PKCE S256 失败，表现为“服务器网络异常，请稍后重试”。
   - 修复：`src/auth/oidc.js` 增加内部 SHA-256 fallback，但仍强制要求 `crypto.getRandomValues`。

2. 后端 JWT 用户映射修复：
   - 问题：当前 Casdoor access token 没有 `preferred_username`，只有 Casdoor 用户名 claim `name`。
   - 修复：`CurrentUserService` 优先读取 `preferred_username`，缺失时回退到 `name`，再查询本地 `user.role`。
   - 安全边界不变：后端只信任已验签 JWT，不信任前端传来的用户名或角色。

3. Casdoor 本地配置：
   - 创建业务组织 `xyyx`，避免把业务用户放进 `built-in` 管理员组织。
   - 创建应用 `xyyx`，Client ID 为 `xyyx-web`。
   - 创建测试用户 `18007300157`。
   - 为匹配临时短密码账号，本机 `xyyx` 组织密码复杂度已放宽；生产环境必须恢复强密码策略。

## 怎么跑

启动 Casdoor：

```powershell
cd C:\Users\Administrator\Desktop\xingyan\deploy\casdoor
docker compose up -d
```

确认当前 issuer：

```powershell
Invoke-WebRequest http://192.168.1.29:8000/.well-known/openid-configuration -UseBasicParsing
```

启动后端：

```powershell
cd C:\Users\Administrator\Desktop\xingyan\xyyx
mvn spring-boot:run "-Dspring-boot.run.arguments=--spring.security.oauth2.resourceserver.jwt.issuer-uri=http://192.168.1.29:8000 --spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://192.168.1.29:8000/.well-known/jwks"
```

启动前端：

```powershell
cd C:\Users\Administrator\Desktop\xingyan\xyyx\xyyx-frontend
$env:VITE_CASDOOR_BASE_URL="http://192.168.1.29:8000"
$env:VITE_CASDOOR_REDIRECT_URI="http://192.168.1.29:5173/auth/callback"
$env:VITE_CASDOOR_CLIENT_ID="xyyx-web"
npm run dev -- --host 0.0.0.0 --strictPort
```

跑验收脚本：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\acceptance\Verify-CasdoorSso.ps1 `
  -CasdoorBaseUrl "http://192.168.1.29:8000" `
  -SystemAClientId "xyyx-web" `
  -SystemARedirectUri "http://192.168.1.29:5173/auth/callback" `
  -SystemAProtectedUrl "http://192.168.1.29:8080/api/me"
```

带真实 access token 的验收不要把 token 写入文件或提交 Git。

## 概念速记

- Organization：Casdoor 里的租户/用户池。业务用户应放在 `xyyx`，不要放在 `built-in`。
- Application：一个接入 SSO 的系统。这里的 `xyyx` 应用代表当前 Vue 前端。
- Client ID：应用公开身份，这里是 `xyyx-web`。前端可以保存 Client ID。
- Client Secret：应用私密凭据。不要放进 Vue 前端；如果必须用 secret，改成后端换 token。
- Redirect URI：登录成功后 Casdoor 允许跳回的地址，必须和前端实际地址完全一致。
- Authorization Code + PKCE：前端安全登录流程。前端先拿授权码，再用 code verifier 换 token，避免授权码被截获后直接滥用。
- Access Token：Casdoor 签发给业务后端验证的 JWT。前端只临时保存，后端用 JWKS 验签。
- JWKS：Casdoor 暴露的公钥集合，后端用它验证 JWT 签名。
- issuer：token 签发者地址。浏览器、后端、Casdoor discovery 必须统一使用同一个 Host。
- `/api/me`：业务系统验收入口；它验证 token 后，把 Casdoor 用户名映射到本地 `user.username`，角色仍来自本地数据库。

## 生产前必须改

- 使用 HTTPS，不要用明文 HTTP。
- 恢复强密码策略，不要沿用本机为短密码临时放宽的规则。
- 收紧 CORS，只允许真实前端域名。
- 如需 `client_secret`，必须改成后端交换 token。
- 继续保持本地 `user.username` 与 Casdoor 用户名一致，否则 `/api/me` 会拒绝登录。
