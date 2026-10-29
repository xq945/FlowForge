#!/usr/bin/env bash
#
# 把 scripts/nacos/ 下的初始配置发布到 Nacos。
#
# 这个脚本是可选的：六个服务的 spring.config.import 都带 optional 前缀，
# Nacos 里没有配置时服务照常启动，直接用 application.yml 里的本地默认值。
# 需要演示「改配置不重启」时才执行它。
#
# 本地 Nacos 关闭了管理端鉴权（见 deploy/docker-compose.yml 的 NACOS_AUTH_ADMIN_ENABLE），
# 所以可以免 token 直接调发布接口。生产环境需要在控制台初始化管理员后，
# 通过 NACOS_ACCESS_TOKEN 传入访问令牌。
#
# 用法：
#   ./scripts/init-nacos-config.sh
#   NACOS_ADDR=nacos:8848 NACOS_ACCESS_TOKEN=xxx ./scripts/init-nacos-config.sh

set -euo pipefail

NACOS_ADDR="${NACOS_ADDR:-127.0.0.1:8848}"
NACOS_GROUP="${NACOS_GROUP:-FLOWFORGE}"
NACOS_NAMESPACE="${NACOS_NAMESPACE:-public}"
NACOS_ACCESS_TOKEN="${NACOS_ACCESS_TOKEN:-}"

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CONF_DIR="$ROOT_DIR/scripts/nacos"

# Git Bash 里的 curl 是 Windows 原生程序，不认 /g/... 这类 MSYS 路径
native_path() {
  if command -v cygpath >/dev/null 2>&1; then
    cygpath -w "$1"
  else
    printf '%s' "$1"
  fi
}

publish() {
  local data_id="$1"
  local file="$2"

  echo "==> 发布 ${NACOS_GROUP}/${data_id}"
  local args=(
    -fsS -o /dev/null
    -X POST "http://${NACOS_ADDR}/nacos/v3/admin/cs/config"
    --data-urlencode "dataId=${data_id}"
    --data-urlencode "groupName=${NACOS_GROUP}"
    --data-urlencode "namespaceId=${NACOS_NAMESPACE}"
    --data-urlencode "type=yaml"
    --data-urlencode "content@$(native_path "$file")"
  )
  if [[ -n "$NACOS_ACCESS_TOKEN" ]]; then
    args+=(-H "accessToken: ${NACOS_ACCESS_TOKEN}")
  fi

  if ! curl "${args[@]}"; then
    echo "发布失败：检查 Nacos 是否已启动，以及管理端鉴权是否已关闭" >&2
    exit 1
  fi
}

publish "flowforge-common.yaml" "$CONF_DIR/flowforge-common.yaml"
publish "flowforge-gateway.yaml" "$CONF_DIR/flowforge-gateway.yaml"

echo ""
echo "配置已写入。控制台: http://localhost:8849  （命名空间 public，分组 ${NACOS_GROUP}）"
echo "热更新验证：改 flowforge-gateway.yaml 里的 burst-capacity 后直接压测，网关无需重启。"
