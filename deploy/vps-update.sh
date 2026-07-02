#!/usr/bin/env bash
# 现有生产 VPS（宝塔/原生部署，如 xujie.xin 那台）更新脚本【模板】。
# 与 vps-bootstrap.sh 无关：不建容器跑服务，只用 Docker 做构建（宿主机无需 JDK/Maven/Node），
# 然后备份并替换现有部署位置的 jar 和前端 dist。
# !!! 下面 6 个变量必须先按你的实际部署路径核对/修改，再运行 !!!
set -euo pipefail

REPO_DIR="${REPO_DIR:-/www/wwwroot/xingyan-src}"                 # 源码 clone 到哪
BRANCH="${BRANCH:-main}"                                          # 部署哪个分支
BACKEND_JAR_DEST="${BACKEND_JAR_DEST:-/www/wwwroot/xyyx/xyyx.jar}" # 线上后端 jar 路径
FRONTEND_DIST_DEST="${FRONTEND_DIST_DEST:-/www/wwwroot/xujie.xin}" # 线上前端静态目录
BACKEND_RESTART_CMD="${BACKEND_RESTART_CMD:-systemctl restart xyyx-backend}" # 重启后端的命令
# 前端构建期变量：必须与线上域名一致
export VITE_CASDOOR_BASE_URL="${VITE_CASDOOR_BASE_URL:-https://sso.xujie.xin}"
export VITE_CASDOOR_REDIRECT_URI="${VITE_CASDOOR_REDIRECT_URI:-https://xujie.xin/auth/callback}"
export VITE_CASDOOR_CLIENT_ID="${VITE_CASDOOR_CLIENT_ID:-xyyx-web}"

STAMP=$(date +%Y%m%d%H%M%S)

echo "[1/4] 拉取代码 ($BRANCH) ..."
cd "$REPO_DIR"
git fetch origin "$BRANCH"
git checkout "$BRANCH"
git pull --ff-only

echo "[2/4] Docker 内构建后端 jar ..."
docker run --rm -v "$PWD/xyyx":/app -v "$HOME/.m2":/root/.m2 -w /app \
  maven:3.9-eclipse-temurin-17 mvn -q -DskipTests package

echo "[3/4] Docker 内构建前端 dist ..."
docker run --rm -v "$PWD/xyyx/xyyx-frontend":/app -w /app \
  -e VITE_CASDOOR_BASE_URL -e VITE_CASDOOR_REDIRECT_URI -e VITE_CASDOOR_CLIENT_ID \
  node:22 sh -c "npm ci && npm run build"

echo "[4/4] 备份并替换线上文件 ..."
if [ -f "$BACKEND_JAR_DEST" ]; then cp "$BACKEND_JAR_DEST" "$BACKEND_JAR_DEST.bak.$STAMP"; fi
cp xyyx/target/*.jar "$BACKEND_JAR_DEST"
tar -C "$FRONTEND_DIST_DEST" -czf "/tmp/frontend-dist-bak-$STAMP.tar.gz" . || true
rsync -a --delete xyyx/xyyx-frontend/dist/ "$FRONTEND_DIST_DEST/"
$BACKEND_RESTART_CMD

echo "完成。注意："
echo "  - 若本次版本在 db/migrations/ 新增了迁移文件，请先备份数据库再手动执行它们；"
echo "  - 前端备份在 /tmp/frontend-dist-bak-$STAMP.tar.gz，后端备份在 $BACKEND_JAR_DEST.bak.$STAMP"
