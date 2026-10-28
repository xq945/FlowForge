#!/usr/bin/env bash
#
# 停止 FlowForge 全栈。
#
#   ./scripts/stop.sh           # 停止容器，保留数据卷
#   ./scripts/stop.sh --purge   # 连同数据卷一起删除（会重跑建表脚本）

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR/deploy"

if [[ "${1:-}" == "--purge" ]]; then
  echo "==> 停止全栈并删除数据卷（数据库将重新初始化）"
  docker compose --profile app down -v
else
  echo "==> 停止全栈（保留数据卷）"
  docker compose --profile app down
fi
