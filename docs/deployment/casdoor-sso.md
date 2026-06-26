# Casdoor SSO 部署说明

最后更新：2026-06-22

## 目标边界

- Casdoor 是 OAuth2/OIDC SSO 身份提供方。
- `xyyx` 后端只信任 Casdoor JWT，不信任前端传来的 `username` / `role` / `operatorUsername`。
- 身份映射固定为：Casdoor `preferred_username` -> 本地 MySQL `user.username`。
- 业务角色固定取本地 MySQL `user.role`，不取前端参数，也不取 Casdoor role claim。

## 本地 Docker 部署

当前仓库提供最小可运行部署入口：

```powershell
cd C:\Users\Administrator\Desktop\xingyan\deploy\casdoor
docker compose up -d
```

部署文件：

- `deploy/casdoor/docker-compose.yml`
- `deploy/casdoor/conf/app.conf`

当前配置使用 SQLite，数据库文件位于 `deploy/casdoor/conf/casdoor.db`，已被 `deploy/casdoor/.gitignore` 忽略。不要把运行时数据库、日志、真实 token、client secret 提交到 Git。

验证 Casdoor：

```powershell
Invoke-WebRequest http://127.0.0.1:8000/.well-known/openid-configuration -UseBasicParsing
Invoke-WebRequest http://127.0.0.1:8000/.well-known/jwks -UseBasicParsing
```

真机测试时不要用 `localhost`。先确认 Windows 当前局域网 IP：

```powershell
Get-NetIPAddress -AddressFamily IPv4 |
  Where-Object { $_.IPAddress -notlike '127.*' -and $_.IPAddress -notlike '169.254.*' } |
  Select-Object InterfaceAlias,IPAddress
```

以当前工作站为例，已验证的 LAN 地址是：

```text
http://192.168.1.29:8000
```

Casdoor discovery 会按访问 Host 返回 issuer。真机测试时后端、前端、浏览器必须统一使用同一个 LAN 地址，例如：

```text
issuer   = http://192.168.1.29:8000
jwks_uri = http://192.168.1.29:8000/.well-known/jwks
```

如果改用其他 IP 或域名，必须重新请求 discovery，以实际返回的 `issuer` / `jwks_uri` 为准。

## Casdoor 应用配置

为 `xyyx` 创建或确认一个前端应用：

- Client ID：`xyyx-web`
- Redirect URI：`http://<LAN_IP>:5173/auth/callback`
- Grant type：`authorization_code`、`refresh_token`
- PKCE：开启，方法 `S256`
- Scope：`openid profile email`
- Token format：JWT
- Claim：`preferred_username` 必须等于本地 `user.username`

如果当前 Casdoor 应用强制要求 `client_secret` 才能换 token，不要把 secret 写进 Vue 前端；应改为后端授权码换 token 接口后再部署。

## 后端启动

不要为了本地验收修改真实 `application.properties`。直接在启动命令注入本次 discovery 得到的真实值：

```powershell
cd C:\Users\Administrator\Desktop\xingyan\xyyx
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.security.oauth2.resourceserver.jwt.issuer-uri=http://<LAN_IP>:8000 --spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://<LAN_IP>:8000/.well-known/jwks"
```

本次真机测试使用：

```powershell
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.security.oauth2.resourceserver.jwt.issuer-uri=http://192.168.1.29:8000 --spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://192.168.1.29:8000/.well-known/jwks"
```

验证：

```powershell
Invoke-WebRequest http://127.0.0.1:8080/api/security/public-key -UseBasicParsing
Invoke-WebRequest http://127.0.0.1:8080/api/me -UseBasicParsing
```

`/api/me` 未带 token 时应返回 `401` 或 `403`。

## 前端启动

真机测试必须把 Casdoor URL 和 callback 都设成 LAN 地址：

```powershell
cd C:\Users\Administrator\Desktop\xingyan\xyyx\xyyx-frontend
$env:VITE_CASDOOR_BASE_URL="http://<LAN_IP>:8000"
$env:VITE_CASDOOR_REDIRECT_URI="http://<LAN_IP>:5173/auth/callback"
$env:VITE_CASDOOR_CLIENT_ID="xyyx-web"
npm run dev -- --host 0.0.0.0 --strictPort
```

本次真机测试使用：

```text
http://192.168.1.29:5173/
```

## 自动验收

基础验收：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\acceptance\Verify-CasdoorSso.ps1 `
  -CasdoorBaseUrl "http://192.168.1.29:8000" `
  -SystemAClientId "xyyx-web" `
  -SystemARedirectUri "http://192.168.1.29:5173/auth/callback"
```

带后端匿名保护检查：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\acceptance\Verify-CasdoorSso.ps1 `
  -CasdoorBaseUrl "http://192.168.1.29:8000" `
  -SystemAClientId "xyyx-web" `
  -SystemARedirectUri "http://192.168.1.29:5173/auth/callback" `
  -SystemAProtectedUrl "http://192.168.1.29:8080/api/me"
```

完整验收仍需要浏览器手工登录，并拿到真实 Casdoor `access_token` 后再跑带 token 的检查：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\acceptance\Verify-CasdoorSso.ps1 `
  -CasdoorBaseUrl "http://192.168.1.29:8000" `
  -SystemAClientId "xyyx-web" `
  -SystemARedirectUri "http://192.168.1.29:5173/auth/callback" `
  -SystemAProtectedUrl "http://192.168.1.29:8080/api/me" `
  -AccessToken "<Casdoor access_token>"
```

不要把真实 token 写入文件或提交到 Git。
