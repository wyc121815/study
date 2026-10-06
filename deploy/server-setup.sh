#!/usr/bin/env bash
#
# 服务器一次性初始化脚本
# 在服务器上以 ubuntu 用户执行（脚本内部自行调用 sudo）
# 幂等：可以重复执行
#
#   scp -r deploy ubuntu@<host>:/tmp/study-deploy
#   ssh ubuntu@<host> 'bash /tmp/study-deploy/server-setup.sh'
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

APP_USER=study
DEPLOY_USER=deploy
APP_DIR=/srv/study
LOG_DIR=/var/log/study
SWAP_FILE=/swapfile

echo "==> 1/7 用户与组"
getent group "$APP_USER" >/dev/null || sudo groupadd --system "$APP_USER"
id -u "$APP_USER" >/dev/null 2>&1 || sudo useradd --system --create-home \
  --home-dir "/home/$APP_USER" --shell /usr/sbin/nologin --gid "$APP_USER" "$APP_USER"
id -u "$DEPLOY_USER" >/dev/null 2>&1 || sudo useradd --create-home --shell /bin/bash "$DEPLOY_USER"
sudo usermod -aG "$APP_USER" "$DEPLOY_USER"
echo "    $APP_USER / $DEPLOY_USER 就绪"

echo "==> 2/7 目录结构"
sudo mkdir -p "$APP_DIR/releases" "$LOG_DIR"
sudo chown -R "$APP_USER:$APP_USER" "$APP_DIR" "$LOG_DIR"
# setgid：新文件自动继承 study 组，deploy 与 study 都能读写
sudo chmod 2775 "$APP_DIR" "$APP_DIR/releases"
sudo chmod 755 "$LOG_DIR"
echo "    $APP_DIR/releases 就绪"

echo "==> 3/7 运行时依赖"
if ! command -v java >/dev/null 2>&1; then
  sudo DEBIAN_FRONTEND=noninteractive apt-get install -y openjdk-21-jre-headless
fi
if ! command -v nginx >/dev/null 2>&1; then
  sudo DEBIAN_FRONTEND=noninteractive apt-get install -y nginx
fi
echo "    $(java -version 2>&1 | head -1)"
echo "    $(nginx -v 2>&1)"

echo "==> 4/7 Swap 兜底（内存 2G）"
if swapon --show=NAME 2>/dev/null | grep -q .; then
  echo "    已存在 swap，跳过"
else
  sudo fallocate -l 2G "$SWAP_FILE" 2>/dev/null || sudo dd if=/dev/zero of="$SWAP_FILE" bs=1M count=2048 status=none
  sudo chmod 600 "$SWAP_FILE"
  sudo mkswap "$SWAP_FILE" >/dev/null
  sudo swapon "$SWAP_FILE"
  grep -q "^$SWAP_FILE" /etc/fstab || echo "$SWAP_FILE none swap sw 0 0" | sudo tee -a /etc/fstab >/dev/null
  echo "    已启用 2G swap"
fi

echo "==> 5/7 systemd 服务"
sudo install -m 644 "$SCRIPT_DIR/study-backend.service" /etc/systemd/system/study-backend.service
sudo systemctl daemon-reload
sudo systemctl enable study-backend >/dev/null 2>&1 || true
echo "    已安装 study-backend.service（等待首次发布后启动）"

echo "==> 6/7 Nginx 站点"
sudo install -m 644 "$SCRIPT_DIR/nginx-study.conf" /etc/nginx/sites-available/study
sudo ln -sfn /etc/nginx/sites-available/study /etc/nginx/sites-enabled/study
sudo rm -f /etc/nginx/sites-enabled/default
sudo nginx -t
sudo systemctl enable nginx >/dev/null 2>&1 || true
sudo systemctl reload nginx
echo "    Nginx 已加载 study 站点"

echo "==> 7/7 deploy 用户的受限 sudo"
sed "s/^deploy /$DEPLOY_USER /" "$SCRIPT_DIR/sudoers-study-deploy" \
  | sudo tee /etc/sudoers.d/90-study-deploy >/dev/null
sudo chmod 440 /etc/sudoers.d/90-study-deploy
sudo visudo -c -f /etc/sudoers.d/90-study-deploy
echo "    已写入 /etc/sudoers.d/90-study-deploy"

echo
echo "初始化完成"
echo "  应用目录 : $APP_DIR/releases"
echo "  运行用户 : $APP_USER（无登录权限）"
echo "  部署用户 : $DEPLOY_USER（受限 sudo）"
