# VPS 部署指南（Docker Compose 一键部署 + 现有生产机更新）

本仓库支持两种部署路径，对应两个脚本：

| 场景 | 脚本 | 说明 |
|------|------|------|
| **新的干净 VPS** | `deploy/vps-bootstrap.sh` | 装 Docker → 生成配置与密钥 → 一条命令起全套服务（MySQL + Casdoor + 后端 + 前端） |
| **现有生产 VPS**（宝塔/原生，如 xujie.xin） | `deploy/vps-update.sh` | 不动现有 MySQL/Casdoor/Nginx，只在 Docker 里构建新版 jar 和前端 dist，备份后替换、重启 |

---

## 一、新 VPS 一键部署

### 前提：仓库是私有的，VPS 需要拉取凭据（一次性配置）

在 VPS 上生成只读部署密钥并添加到 GitHub：

```bash
ssh-keygen -t ed25519 -f ~/.ssh/xingyan_deploy -N ""
cat ~/.ssh/xingyan_deploy.pub
# 复制输出 → GitHub 仓库页 Settings → Deploy keys → Add deploy key（不勾选写权限）
cat >> ~/.ssh/config <<'EOF'
Host github.com
  IdentityFile ~/.ssh/xingyan_deploy
EOF
```

### 部署（共两条命令）

```bash
git clone git@github.com:M13-Perfect/xingyan.git && cd xingyan
bash deploy/vps-bootstrap.sh
```

第一次运行会自动生成 `deploy/stack/.env`（随机密码/密钥已填好），并提示你把其中的
`FRONTEND_PUBLIC_URL`、`CASDOOR_PUBLIC_URL` 改成本机公网 IP 或域名。改完再跑一次
`bash deploy/vps-bootstrap.sh` 即完成部署。

### .env 配置项说明

| 变量 | 含义 | 备注 |
|------|------|------|
| `FRONTEND_PUBLIC_URL` | 浏览器打开业务系统的地址 | 例 `http://1.2.3.4` 或 `https://xxx.com`，不带末尾斜杠 |
| `CASDOOR_PUBLIC_URL` | 浏览器访问认证服务的地址 | 例 `http://1.2.3.4:8000`；token 的 issuer 等于它，改了必须重建前后端容器 |
| `FRONTEND_PORT` / `CASDOOR_PORT` | 宿主机端口 | 与上面地址里的端口一致 |
| `DB_ROOT_PASSWORD` | MySQL root 密码 | `GENERATE` 占位符会被自动替换成随机值 |
| `CASDOOR_CLIENT_SECRET` | xyyx-web 应用密钥 | 自动生成并随种子写入 Casdoor，后端管理接口用它 |
| `XYYX_PHONE_ENC_KEYS` / `XYYX_PHONE_HASH_KEYS` | 手机号加密/索引密钥 | **一旦库里有数据就不可更换**，否则已加密手机号无法解密（详见 docs/database/phone-privacy-operator.md） |

### 首次启动后（约 2 分钟人工步骤）

Casdoor 的组织（xyyx）、应用(xyyx-web，含回调地址和 Client Secret）已由
`deploy/stack/casdoor-conf/init_data.json`（bootstrap 从模板自动生成）种入，无需手工建。
还需要做的：

1. 打开 `CASDOOR_PUBLIC_URL`，用 Casdoor 内置账号 `admin / 123` 登录，**立即修改此密码**；
2. 在 Casdoor「用户」中给 `xyyx` 组织创建业务账号——用户名必须与业务库 `user` 表中的账号一致
   （管理员种子账号见 `xyyx_db.sql` 的 user 表 INSERT；员工可后续用系统内「开通新员工」创建，会自动双写）；
3. 打开 `FRONTEND_PUBLIC_URL`，用该账号登录业务系统。

### 日常更新

```bash
cd xingyan && git pull && cd deploy/stack && docker compose up -d --build
```

若更新内容包含 `db/migrations/` 新文件（MySQL 数据卷已初始化过，不会自动执行），先备份再手动跑：

```bash
cd deploy/stack
docker compose exec mysql sh -c 'mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" xyyx_db' > ~/xyyx_db-backup-$(date +%F).sql
docker compose exec -T mysql sh -c 'mysql --default-character-set=utf8mb4 -uroot -p"$MYSQL_ROOT_PASSWORD" xyyx_db' < ../../db/migrations/<新迁移文件>.sql
```

### 备份

- 业务数据：上面的 `mysqldump` 命令；
- 认证数据：`deploy/stack/casdoor-conf/casdoor.db`（SQLite 单文件，直接复制）。

### 已知限制

- 纯 IP + HTTP 部署时，浏览器禁用 `window.crypto.subtle`，「开通新员工 / 修改密码」页面不可用
  （登录和其余功能不受影响，有降级路径）。挂上域名 + HTTPS 后自然解除。
- HTTPS 方案：在宿主机再加一层 Nginx/Caddy 反代 80/8000 并签证书，`.env` 里两个 PUBLIC_URL
  改成 https 域名后 `docker compose up -d --build` 重建即可。

---

## 二、现有生产 VPS 更新（vps-update.sh）

`deploy/vps-update.sh` 是**模板**：顶部 6 个变量（源码目录、分支、线上 jar 路径、前端目录、
重启命令、前端构建域名）必须先按宝塔里的实际路径核对/修改。它做的事：

1. `git pull` 拉最新代码；
2. 在 Docker 容器里构建后端 jar 和前端 dist（宿主机不需要装 JDK/Maven/Node）；
3. 备份现有 jar（`.bak.时间戳`）和前端目录（`/tmp/frontend-dist-bak-*.tar.gz`）后替换；
4. 执行重启命令。

数据库迁移**永远不自动执行**：若版本包含新迁移，先备份库再手动跑（同上）。

---

## 三、常见问题

| 症状 | 原因/处理 |
|------|-----------|
| 登录报"无法连接认证服务" | `CASDOOR_PUBLIC_URL` 与浏览器实际能访问的地址不一致，或云防火墙没放行 `CASDOOR_PORT` |
| 登录报 Redirect URI 错误 | Casdoor 里 xyyx-web 应用的回调地址与 `FRONTEND_PUBLIC_URL/auth/callback` 不一致（改过域名后需在 Casdoor 控制台同步） |
| 后端 401 | token 的 issuer 与后端配置不一致：确认 `.env` 的 `CASDOOR_PUBLIC_URL` 改动后重建了 backend 和 frontend 两个容器 |
| 员工管理/开通员工报错 | `CASDOOR_CLIENT_SECRET` 与 Casdoor 里应用的实际 Secret 不一致（控制台可查改） |
