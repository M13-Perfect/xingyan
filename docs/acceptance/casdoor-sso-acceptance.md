# Casdoor SSO 验收说明

最后更新：2026-06-22

## 验收目标

Casdoor 作为统一 SSO 鉴权中心，至少服务两个独立系统：

- 系统 A：`xyyx`
- 系统 B：后续独立系统或临时测试系统

验收通过标准：用户在同一个浏览器里登录一次 Casdoor 后，进入第二个系统时不再输入密码；两个系统后端都只信任 Casdoor 签发的 Token，不再信任前端传来的 `username` / `role`。

## 自动验收脚本

脚本位置：

```powershell
scripts/acceptance/Verify-CasdoorSso.ps1
```

脚本自检：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\acceptance\Verify-CasdoorSso.ps1 -SelfTest
```

Casdoor 基础验收：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\acceptance\Verify-CasdoorSso.ps1 `
  -CasdoorBaseUrl "http://localhost:8000" `
  -SystemAClientId "xyyx-web" `
  -SystemARedirectUri "http://localhost:5173/auth/callback" `
  -SystemBClientId "future-system-web" `
  -SystemBRedirectUri "http://localhost:5174/auth/callback"
```

接入后端后的完整验收：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\acceptance\Verify-CasdoorSso.ps1 `
  -CasdoorBaseUrl "http://localhost:8000" `
  -SystemAClientId "xyyx-web" `
  -SystemARedirectUri "http://localhost:5173/auth/callback" `
  -SystemAProtectedUrl "http://localhost:8080/api/me" `
  -SystemBClientId "future-system-web" `
  -SystemBRedirectUri "http://localhost:5174/auth/callback" `
  -SystemBProtectedUrl "http://localhost:8081/api/me" `
  -AccessToken "<从浏览器或调试工具中取得的 Casdoor access_token>"
```

不要把真实 Token 提交到 Git。

## 实际测试内容

1. **OIDC 发现地址可用**
   - 请求：`/.well-known/openid-configuration`
   - 通过标准：返回 `issuer`、`authorization_endpoint`、`token_endpoint`、`userinfo_endpoint`、`jwks_uri`
   - 失败含义：Casdoor 未启动、反向代理路径错误，或 OIDC 未正确暴露

2. **支持授权码模式**
   - 检查：`grant_types_supported` 包含 `authorization_code`
   - 通过标准：两个前端系统都可以走标准 OIDC 登录跳转
   - 失败含义：不能作为标准 SSO Provider 使用

3. **支持 Refresh Token**
   - 检查：`grant_types_supported` 包含 `refresh_token`
   - 通过标准：后续可实现登录续期
   - 失败含义：用户会频繁重新登录，生产体验差

4. **支持 PKCE S256**
   - 检查：`code_challenge_methods_supported` 包含 `S256`
   - 通过标准：Vue/Vite 这类前端应用可以使用 Authorization Code + PKCE
   - 失败含义：前端授权码可能被截获，安全边界不够

5. **JWKS 公钥可用**
   - 请求：`jwks_uri`
   - 通过标准：返回至少一个 RSA 公钥
   - 失败含义：业务后端无法本地验签 Casdoor Token

6. **两个系统都能生成授权入口**
   - 系统 A：`xyyx-web`
   - 系统 B：`future-system-web`
   - 通过标准：两个授权 URL 都包含 `response_type=code` 和 `code_challenge_method=S256`
   - 失败含义：Client ID、Redirect URI 或 Casdoor 应用配置错误

7. **授权入口可访问**
   - 请求：两个系统的授权 URL
   - 通过标准：返回登录页或跳转状态码 `200/302/303`
   - 失败含义：应用未注册、回调地址不匹配、Casdoor 路由不可达

8. **未登录访问业务接口必须被拒绝**
   - 请求：不带 Token 访问 `/api/me` 或任意受保护接口
   - 通过标准：返回 `401` 或 `403`
   - 失败含义：业务系统仍存在未授权访问漏洞

9. **带 Casdoor Token 访问业务接口必须通过**
   - 请求：带 `Authorization: Bearer <access_token>` 访问两个系统受保护接口
   - 通过标准：两个系统返回 `2xx`
   - 失败含义：Resource Server 配置错误，或后端没有信任 Casdoor Issuer/JWKS

10. **Token 身份声明可信**
    - 检查：`iss`、`sub`、`aud`、`exp`、`kid`
    - 通过标准：
      - `iss` 等于 Casdoor issuer
      - `sub` 存在
      - `aud` 存在
      - `exp` 未过期
      - `kid` 能在 JWKS 中找到
    - 失败含义：Token 不是 Casdoor 签发，或密钥配置不一致

11. **浏览器单点登录人工验收**
    - 操作：
      1. 打开脚本输出的系统 A 授权 URL
      2. 在 Casdoor 登录
      3. 再打开系统 B 授权 URL
    - 通过标准：系统 B 不再要求输入密码，直接完成登录/授权跳转
    - 失败含义：Casdoor 会话 Cookie、域名、HTTPS、SameSite 或应用配置存在问题

## 通过判定

自动脚本退出码为 `0`，并且人工浏览器单点登录验收通过，才能判定 SSO PoC 通过。

只通过自动脚本但没有人工浏览器验证，不算完整 SSO 通过。
