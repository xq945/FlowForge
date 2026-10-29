# 本地开发指南

## 1. 前置条件

| 依赖 | 版本 | 校验命令 |
| --- | --- | --- |
| JDK | 21 | `java -version` |
| Maven | 3.9+ | `mvn -version` |
| Docker | 24+ 且启用 Compose v2 | `docker compose version` |

## 2. 端口分配

本项目刻意避开本机常见默认端口，避免与其它项目冲突：

| 组件 | 宿主机端口 | 容器端口 | 说明 |
| --- | --- | --- | --- |
| MySQL | 3307 | 3306 | |
| Redis | 6380 | 6379 | |
| RabbitMQ (AMQP) | 5674 | 5672 | |
| RabbitMQ 控制台 | 15674 | 15672 | 账号 `flowforge` / `flowforge` |
| Nacos 客户端 | 8848 / 9848 | 8848 / 9848 | HTTP / gRPC |
| Nacos 控制台 | 8849 | 8080 | 打开后按页面引导初始化管理员账户 |
| XXL-JOB 调度中心 | 8090 | 8080 | `admin` / `123456` |
| XXL-JOB 执行器 | 9999 | - | 由 scheduler-service 启动 |
| user-service | 8081 | - | |
| gateway | 8083 | - | 8080/8082 常被其它服务占用 |
| workflow-service | 8084 | - | |
| scheduler-service | 8085 | - | |
| execution-service | 8086 | - | |

## 3. 启动基础设施

```bash
cd deploy
docker compose up -d
docker compose ps
```

四个容器全部 `healthy` 后再启动应用。

> 首次启动会自动执行 `deploy/mysql/init/*.sql` 建库建表。
> 修改建表脚本后，需要 `docker compose down -v` 清空数据卷，否则脚本不会重跑。

## 4. 启动应用

基础设施就绪后，在两个终端分别启动：

```bash
mvn -pl flowforge-user-service        spring-boot:run
mvn -pl flowforge-workflow-service    spring-boot:run
mvn -pl flowforge-execution-service   spring-boot:run
mvn -pl flowforge-scheduler-service   spring-boot:run
mvn -pl flowforge-gateway             spring-boot:run
```

或直接运行各自的 `*Application` 主类。默认激活 `local` profile，连接上面映射到宿主机的端口。

> 服务之间通过 Nacos 服务名互相调用，**启动顺序无强制要求**，但网关路由依赖下游已注册，
> 首次调用前请确认目标服务已出现在 Nacos 服务列表中。

### 4.1 启动前端

```bash
cd flowforge-frontend
npm install
npm run dev
```

访问 http://localhost:5173 。开发服务器把 `/api` 代理到网关（8083），不需要额外配置跨域。

| 命令 | 说明 |
| --- | --- |
| `npm run dev` | 开发服务器，带热更新 |
| `npm run build` | 类型检查 + 生产构建，产物在 `dist/` |

> Vite 默认可能只监听 `[::1]`，本项目已在 `vite.config.ts` 显式设置 `host: true`，
> 确保 `127.0.0.1` 与局域网 IP 都能访问。

## 5. 验证

```bash
# 1. 注册
curl -X POST http://localhost:8083/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"demo_user","password":"demo1234","displayName":"演示用户","email":"demo@example.com"}'

# 2. 登录，取 token
curl -X POST http://localhost:8083/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"demo_user","password":"demo1234"}'

# 3. 带 token 访问
curl http://localhost:8083/api/users/me \
  -H "Authorization: Bearer <上一步返回的 token>"

# 4. 不带 token 应返回 401
curl -i http://localhost:8083/api/users/me
```

### 5.1 工作流闭环

```bash
# 1. 创建工作流（草稿）
curl -X POST http://localhost:8083/api/workflows \
  -H "Content-Type: application/json" -H "Authorization: Bearer <token>" \
  --data-binary @workflow.json

# 2. 发布（严格 DAG 校验通过后生成不可变版本）
curl -X POST http://localhost:8083/api/workflows/1/publish -H "Authorization: Bearer <token>"

# 3. 手动触发，requestId 是幂等键：重复点击只会产生一个实例
curl -X POST http://localhost:8083/api/executions \
  -H "Content-Type: application/json" -H "Authorization: Bearer <token>" \
  -d '{"workflowId":1,"requestId":"req-001"}'

# 4. 查看执行详情（含每个节点状态与失败原因）
curl http://localhost:8083/api/executions/1 -H "Authorization: Bearer <token>"

# 5. 查看某节点的每次尝试
curl http://localhost:8083/api/executions/1/nodes/1/attempts -H "Authorization: Bearer <token>"
```

> Windows 下直接用 `-d` 传中文会因终端编码变成 GBK，导致「请求体格式错误」。
> 把 JSON 写进 UTF-8 文件再用 `--data-binary @file` 即可。

## 6. 配置说明

| 配置项 | 环境变量 | 本地默认值 | 说明 |
| --- | --- | --- | --- |
| JWT 密钥 | `FLOWFORGE_JWT_SECRET` | 见 `application-local.yml` | 长度需 ≥ 32 字节；网关与用户服务必须一致 |
| MySQL 地址 | `MYSQL_HOST` / `MYSQL_PORT` | `127.0.0.1:3307` | |
| MySQL 账号 | `MYSQL_USER` / `MYSQL_PASSWORD` | `flowforge` / `flowforge` | |
| Redis 地址 | `REDIS_HOST` / `REDIS_PORT` | `127.0.0.1:6380` | |
| Nacos 地址 | `NACOS_SERVER_ADDR` | `127.0.0.1:8848` | 服务发现与配置中心共用 |
| Nacos 账号 | `NACOS_USERNAME` / `NACOS_PASSWORD` | 空 | 本地关闭鉴权，留空即不登录 |

`local` 与 `docker` 两个 profile 的差异只在于**地址**：本地连映射端口，容器内连服务名。

### 6.1 配置中心（可选）

六个服务会从 Nacos 读取 `flowforge-common.yaml` 和 `flowforge-<服务名>.yaml`（分组 `FLOWFORGE`）。
这一步是**可选**的：import 带 `optional:` 前缀，Nacos 里没有配置时服务照常用本地 yml 启动。

```bash
# 把初始配置写进 Nacos（本地关闭了管理端鉴权，免 token）
./scripts/init-nacos-config.sh

# 控制台：http://localhost:8849 ，命名空间 public，分组 FLOWFORGE
```

写进 Nacos 的只有**可热更新**的参数：日志级别、网关限流阈值。
改完保存后不用重启，网关日志会出现 `Receive Nacos config change` 与 `Refresh keys changed`。

```bash
# 演示热更新：把突发容量改小，然后连续请求观察 429
curl -X POST http://127.0.0.1:8848/nacos/v3/admin/cs/config \
  --data-urlencode "dataId=flowforge-gateway.yaml" \
  --data-urlencode "groupName=FLOWFORGE" \
  --data-urlencode "namespaceId=public" \
  --data-urlencode "type=yaml" \
  --data-urlencode "content=flowforge:
  gateway:
    rate-limit:
      enabled: true
      replenish-rate: 1
      burst-capacity: 3"
```

`@Scheduled` 的扫描间隔（重试扫描、超时扫描、Outbox 发布）**不能**热更新，改它们需要重启服务。

## 7. 常见问题

| 现象 | 原因 | 处理 |
| --- | --- | --- |
| 启动报 `UnknownHostException: mysql` | 用了 `docker` profile 但在本机跑 | 改用 `local` profile |
| 登录返回 401 但密码正确 | 网关与用户服务 JWT 密钥不一致 | 检查两边的 `FLOWFORGE_JWT_SECRET` |
| 注册返回 `U1001` 但用户名没重复 | 上一次注册已成功 | 换用户名，或直接登录 |
| 建表脚本改了但没生效 | 初始化脚本只在数据卷为空时执行 | `docker compose down -v` 后重启 |
| Nacos 注册失败 403 | 3.x 默认开启鉴权 | 确认 `NACOS_USERNAME` / `NACOS_PASSWORD` 正确 |
