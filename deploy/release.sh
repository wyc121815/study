#!/usr/bin/env bash
#
# 在服务器上由 deploy 用户执行：切换发布版本并用 docker compose 拉起整栈。
#
#   bash /tmp/study-release.sh <版本时间戳> [HTTP端口]
#
set -euo pipefail

TS="${1:?用法: release.sh <版本时间戳>}"
HTTP_PORT="${2:-80}"

APP_DIR=/srv/study
REL="$APP_DIR/releases/$TS"
SHARED="$APP_DIR/shared"
PROJECT=conn-platform
HEALTH_URL="http://127.0.0.1:${HTTP_PORT}/health"

if [ ! -f "$REL/backend/gateway-service.jar" ]; then
  echo "错误: 发布包不完整，找不到 $REL/backend/gateway-service.jar"
  exit 1
fi

# 密钥集中放在 shared 下，每个版本软链过去，避免每份发布包都留一份明文
mkdir -p "$SHARED"
if [ -f "$REL/.env" ]; then
  mv -f "$REL/.env" "$SHARED/.env"
fi
if [ ! -f "$SHARED/.env" ]; then
  echo "错误: 缺少 $SHARED/.env（密钥文件）"
  exit 1
fi
chmod 600 "$SHARED/.env"
ln -sfn "$SHARED/.env" "$REL/.env"

PREV="$(readlink -f "$APP_DIR/current" 2>/dev/null || true)"
echo "上一版本: ${PREV:-无}"

echo "切换软链: $APP_DIR/current -> $REL"
ln -sfn "$REL" "$APP_DIR/current"

compose_up() {
  local dir="$1"
  docker compose \
    --project-name "$PROJECT" \
    --project-directory "$dir" \
    -f "$dir/docker-compose.yml" \
    up -d --build --remove-orphans
}

echo "构建镜像并启动容器（首次会慢一些）"
compose_up "$REL"

echo "等待健康检查 $HEALTH_URL ..."
for i in $(seq 1 40); do
  if curl -fsS --max-time 3 "$HEALTH_URL" >/dev/null 2>&1; then
    echo "健康检查通过（第 ${i} 次尝试）"
    exit 0
  fi
  sleep 3
done

echo "健康检查失败"
if [ -n "$PREV" ] && [ -f "$PREV/docker-compose.yml" ]; then
  echo "回滚到 $PREV"
  ln -sfn "$PREV" "$APP_DIR/current"
  compose_up "$PREV"
fi
exit 1
