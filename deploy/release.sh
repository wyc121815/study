#!/usr/bin/env bash
#
# 在服务器上由 deploy 用户执行：切换发布版本并重启后端
# 用法: bash /tmp/study-release.sh <版本时间戳>
#
set -euo pipefail

TS="${1:?用法: release.sh <版本时间戳>}"
APP_DIR=/srv/study
REL="$APP_DIR/releases/$TS"
HEALTH_URL=http://127.0.0.1:8080/actuator/health

if [ ! -f "$REL/backend.jar" ]; then
  echo "错误: 找不到 $REL/backend.jar"
  exit 1
fi

# 记录回滚点
PREV="$(readlink -f "$APP_DIR/current" 2>/dev/null || true)"
echo "上一版本: ${PREV:-无}"

echo "切换软链: $APP_DIR/current -> $REL"
ln -sfn "$REL" "$APP_DIR/current"

echo "重启后端服务"
sudo -n /usr/bin/systemctl restart study-backend

echo "等待健康检查..."
for i in $(seq 1 30); do
  if curl -fsS --max-time 3 "$HEALTH_URL" >/dev/null 2>&1; then
    echo "健康检查通过（第 ${i} 次尝试）"
    exit 0
  fi
  sleep 2
done

echo "健康检查失败"
if [ -n "$PREV" ] && [ -f "$PREV/backend.jar" ]; then
  echo "回滚到 $PREV"
  ln -sfn "$PREV" "$APP_DIR/current"
  sudo -n /usr/bin/systemctl restart study-backend
fi
exit 1
