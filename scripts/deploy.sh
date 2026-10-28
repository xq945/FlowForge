#!/usr/bin/env bash
#
# FlowForge 一键部署
#
# 构建顺序有讲究：
#   1. 后端先 mvn package —— 镜像只装 JRE + jar，不想在容器里重新下载依赖
#   2. 前端先 npm run build —— 同理，镜像只装静态资源
#   3. 最后 docker compose 构建镜像并启动全栈
#
# 用法：
#   ./scripts/deploy.sh            # 全量构建并启动
#   ./scripts/deploy.sh --skip-build   # 用现有产物直接启动

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

SKIP_BUILD=false
if [[ "${1:-}" == "--skip-build" ]]; then
  SKIP_BUILD=true
fi

if [[ "$SKIP_BUILD" == "false" ]]; then
  echo "==> [1/3] 构建后端（Maven 多模块）"
  ./mvnw -B clean package -DskipTests

  echo "==> [2/3] 构建前端（Vite）"
  (cd flowforge-frontend && npm ci --no-audit --no-fund && npm run build)
else
  echo "==> 跳过构建，使用现有产物"
fi

for jar in flowforge-user-service flowforge-workflow-service flowforge-execution-service \
           flowforge-scheduler-service flowforge-notification-service flowforge-gateway; do
  if ! ls "$jar"/target/*.jar >/dev/null 2>&1; then
    echo "缺少 $jar 的构建产物，请先执行 ./mvnw clean package -DskipTests" >&2
    exit 1
  fi
done
if [[ ! -d flowforge-frontend/dist ]]; then
  echo "缺少前端 dist，请先执行 npm run build" >&2
  exit 1
fi

echo "==> [3/3] 构建镜像并启动全栈"
cd deploy
docker compose --profile app up -d --build

echo ""
echo "全栈已启动，等待服务注册到 Nacos（约 60 秒）..."
echo "  前端入口:  http://localhost:8088"
echo "  Nacos:     http://localhost:8849"
echo "  XXL-JOB:   http://localhost:8090/xxl-job-admin  (admin / 123456)"
echo ""
echo "查看状态: docker compose --profile app ps"
echo "查看日志: docker compose --profile app logs -f flowforge-execution-service"
