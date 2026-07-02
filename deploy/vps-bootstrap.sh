#!/usr/bin/env bash
# 新 VPS 一键部署（Ubuntu/Debian/CentOS 均可，需 root 或 docker 权限）：
#   git clone <本仓库> && cd xingyan && bash deploy/vps-bootstrap.sh
# 第一次运行会生成 deploy/stack/.env 并提示填写公网地址；填好后再跑一次即完成部署。
set -euo pipefail
cd "$(dirname "$0")/stack"

if ! command -v docker >/dev/null 2>&1; then
  echo "[1/4] 安装 Docker ..."
  curl -fsSL https://get.docker.com | sh
  (command -v systemctl >/dev/null 2>&1 && systemctl enable --now docker) || true
else
  echo "[1/4] Docker 已安装，跳过"
fi

echo "[2/4] 准备配置 ..."
if [ ! -f .env ]; then
  cp .env.example .env
  # GENERATE 占位符 -> 自动生成随机值
  for var in DB_ROOT_PASSWORD CASDOOR_CLIENT_SECRET; do
    grep -q "^${var}=GENERATE$" .env && sed -i "s|^${var}=GENERATE$|${var}=$(openssl rand -hex 16)|" .env
  done
  for var in XYYX_PHONE_ENC_KEYS XYYX_PHONE_HASH_KEYS; do
    grep -q "^${var}=GENERATE$" .env && sed -i "s|^${var}=GENERATE$|${var}=v1:$(openssl rand -base64 32)|" .env
  done
  echo "已生成 deploy/stack/.env（随机密钥已填好）。"
fi
if grep -Eq "^(FRONTEND_PUBLIC_URL|CASDOOR_PUBLIC_URL)=.*your-vps-ip" .env; then
  echo ""
  echo ">>> 还差一步：编辑 deploy/stack/.env，把 FRONTEND_PUBLIC_URL 和 CASDOOR_PUBLIC_URL"
  echo ">>> 里的 your-vps-ip 换成本机公网 IP 或域名，然后重新运行本脚本。"
  exit 1
fi

# 仅首次（casdoor.db 还不存在时）由模板生成 Casdoor 种子数据；之后不再覆盖，避免动线上配置
if [ ! -f casdoor-conf/casdoor.db ]; then
  FRONTEND_PUBLIC_URL=$(grep '^FRONTEND_PUBLIC_URL=' .env | cut -d= -f2-)
  CASDOOR_CLIENT_SECRET=$(grep '^CASDOOR_CLIENT_SECRET=' .env | cut -d= -f2-)
  sed -e "s|__FRONTEND_PUBLIC_URL__|${FRONTEND_PUBLIC_URL}|g" \
      -e "s|__CASDOOR_CLIENT_SECRET__|${CASDOOR_CLIENT_SECRET}|g" \
      casdoor-conf/init_data.json.template > casdoor-conf/init_data.json
  echo "已生成 Casdoor 种子（组织 xyyx + 应用 xyyx-web + 回调地址）。"
fi

echo "[3/4] 构建并启动（首次构建要下载依赖，可能需要几分钟）..."
docker compose up -d --build

echo "[4/4] 当前状态："
docker compose ps
echo ""
echo "部署完成。首次使用步骤："
echo "  1. 浏览器打开 CASDOOR_PUBLIC_URL，用内置账号 admin / 123 登录 Casdoor 控制台，立即修改该密码；"
echo "  2. 在 Casdoor \"用户\" 里为 xyyx 组织创建业务账号（用户名需与业务库 user 表一致，管理员种子账号见 xyyx_db.sql）；"
echo "  3. 浏览器打开 FRONTEND_PUBLIC_URL 用该账号登录业务系统。"
