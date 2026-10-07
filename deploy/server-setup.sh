#!/usr/bin/env bash
#
# 服务器一次性初始化脚本（Ubuntu / Debian）。
#
#   scp -r deploy ubuntu@<host>:/tmp/study-deploy
#   ssh ubuntu@<host> 'bash /tmp/study-deploy/server-setup.sh'
#
# 幂等：可以重复执行。
#
# 注意：把用户加入 docker 组等于给了 root 权限（docker 可以挂载宿主目录）。
# 这是容器化部署的固有代价，务必只把部署账号加入该组，并保护好 SSH 私钥。
#
set -euo pipefail

DEPLOY_USER=deploy
APP_DIR=/srv/study
HTTP_PORT="${HTTP_PORT:-80}"

echo "==> 1/6 部署用户"
id -u "$DEPLOY_USER" >/dev/null 2>&1 || sudo useradd --create-home --shell /bin/bash "$DEPLOY_USER"
sudo mkdir -p "$DEPLOY_USER/.ssh"
sudo chmod 700 "$DEPLOY_USER/.ssh"
echo "    $DEPLOY_USER 就绪"

echo "==> 2/6 目录结构"
sudo mkdir -p "$APP_DIR/releases" "$APP_DIR/shared"
sudo chown -R "$DEPLOY_USER:$DEPLOY_USER" "$APP_DIR"
sudo chmod 750 "$APP_DIR" "$APP_DIR/releases" "$APP_DIR/shared"
echo "    $APP_DIR/releases 就绪"

echo "==> 3/6 安装 Docker"
if ! command -v docker >/dev/null 2>&1; then
  sudo install -m 0755 -d /etc/apt/keyrings
  curl -fsSL https://download.docker.com/linux/ubuntu/gpg \
    | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
  sudo chmod a+r /etc/apt/keyrings/docker.gpg
  echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] \
https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" \
    | sudo tee /etc/apt/sources.list.d/docker.list >/dev/null
  sudo apt-get update
  sudo DEBIAN_FRONTEND=noninteractive apt-get install -y \
    docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
fi
sudo systemctl enable --now docker
sudo usermod -aG docker "$DEPLOY_USER"
echo "    $(docker --version)"
echo "    $(docker compose version | head -1)"

echo "==> 4/6 内核与交换文件"
# 单机跑 MySQL + Nacos + 3 个服务，swap 是必要的兜底
if ! swapon --show=NAME 2>/dev/null | grep -q .; then
  sudo fallocate -l 2G /swapfile 2>/dev/null || sudo dd if=/dev/zero of=/swapfile bs=1M count=2048 status=none
  sudo chmod 600 /swapfile
  sudo mkswap /swapfile >/dev/null
  sudo swapon /swapfile
  grep -q '^/swapfile' /etc/fstab || echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab >/dev/null
  echo "    已启用 2G swap"
else
  echo "    已存在 swap，跳过"
fi

# 让内核对容器友好一些
sudo sysctl -w vm.max_map_count=262144 >/dev/null

echo "==> 5/6 防火墙"
if command -v ufw >/dev/null 2>&1; then
  sudo ufw allow "${HTTP_PORT}/tcp" >/dev/null 2>&1 || true
  echo "    已放行端口 ${HTTP_PORT}"
else
  echo "    未安装 ufw，跳过"
fi

echo "==> 6/6 fail2ban 防爆破"
if [ -f "$SCRIPT_DIR/fail2ban-jail.conf" ]; then
  if ! command -v fail2ban-server >/dev/null 2>&1; then
    sudo DEBIAN_FRONTEND=noninteractive apt-get install -y fail2ban python3-systemd
  fi
  sudo install -m 644 "$SCRIPT_DIR/fail2ban-jail.conf" /etc/fail2ban/jail.d/study-sshd.local
  sudo systemctl enable fail2ban >/dev/null 2>&1 || true
  sudo systemctl restart fail2ban
  sleep 3
  sudo fail2ban-client status
else
  echo "    未找到 fail2ban-jail.conf，跳过"
fi

echo
echo "初始化完成"
echo "  应用目录 : $APP_DIR/releases"
echo "  部署用户 : $DEPLOY_USER（需要重新登录才能生效 docker 组）"
echo "  密钥文件 : $APP_DIR/shared/.env（由首次发布写入）"
