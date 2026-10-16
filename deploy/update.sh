#!/usr/bin/env bash
# 服务器侧发布脚本：拉代码 → 构建 → 换产物 → 重启。
#
# 用法（在 ECS 上）：
#   cd /opt/jotang/src && bash deploy/update.sh
# 本地一条命令触发：
#   ssh <user>@<IP> 'cd /opt/jotang/src && bash deploy/update.sh'
#
# 前提：deploy/.env 与 /etc/jotang/jotang.env 都已就位（见 docs/部署运维.md）。
# 执行者需要对 systemctl 的免密 sudo；不要把整个脚本用 sudo 跑，
# 否则 target/ 与 node_modules/ 会变成 root 属主。

set -euo pipefail

SRC=/opt/jotang/src
APP=/opt/jotang/app.jar
WEB=/var/www/jotang
SERVICE=jotang-note
COMPOSE="docker compose -f deploy/docker-compose.prod.yml"
JAR=jotang-note-0.0.1-SNAPSHOT.jar

cd "$SRC"

echo "==> 拉取最新代码"
# --ff-only：服务器上有本地改动时直接失败，而不是悄悄产生一个合并提交
git pull --ff-only

echo "==> 停掉应用与容器，把整台机器的内存让给构建"
# 2 核 2G 上这一步不是可选项：三个容器常驻约 700MB，不腾出来的话
# vite build 的峰值内存会把机器打到 swap 抖动甚至 OOM。
# 反正应用本来就要停，容器一起停不影响可用性。
sudo systemctl stop "$SERVICE" || true
$COMPOSE stop

echo "==> 构建后端"
./mvnw -q clean package -DskipTests

echo "==> 构建前端"
cd frontend
npm ci
npm run build
cd ..

echo "==> 替换产物"
cp "target/$JAR" "$APP"
rsync -a --delete frontend/dist/ "$WEB/"

echo "==> 拉起容器并等健康检查通过"
# 应用启动时就去连 MySQL 与 Broker，--wait 保证它们真的就绪了再启应用
$COMPOSE up -d --wait

echo "==> 启动"
sudo systemctl start "$SERVICE"
sudo systemctl --no-pager --lines=20 status "$SERVICE" || true
