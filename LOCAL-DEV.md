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
| gateway | 8083 | - | 8080 常被其它服务占用 |
| user-service | 8081 | - | |

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
# 终端 1
mvn -pl flowforge-user-service spring-boot:run

# 终端 2
mvn -pl flowforge-gateway spring-boot:run
```

或直接运行各自的 `*Application` 主类。默认激活 `local` profile，连接上面映射到宿主机的端口。

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

## 6. 配置说明

| 配置项 | 环境变量 | 本地默认值 | 说明 |
| --- | --- | --- | --- |
| JWT 密钥 | `FLOWFORGE_JWT_SECRET` | 见 `application-local.yml` | 长度需 ≥ 32 字节；网关与用户服务必须一致 |
| MySQL 地址 | `MYSQL_HOST` / `MYSQL_PORT` | `127.0.0.1:3307` | |
| MySQL 账号 | `MYSQL_USER` / `MYSQL_PASSWORD` | `flowforge` / `flowforge` | |
| Redis 地址 | `REDIS_HOST` / `REDIS_PORT` | `127.0.0.1:6380` | |
| Nacos 地址 | `NACOS_SERVER_ADDR` | `127.0.0.1:8848` | |
| Nacos 账号 | `NACOS_USERNAME` / `NACOS_PASSWORD` | `nacos` / `nacos` | |

`local` 与 `docker` 两个 profile 的差异只在于**地址**：本地连映射端口，容器内连服务名。

## 7. 常见问题

| 现象 | 原因 | 处理 |
| --- | --- | --- |
| 启动报 `UnknownHostException: mysql` | 用了 `docker` profile 但在本机跑 | 改用 `local` profile |
| 登录返回 401 但密码正确 | 网关与用户服务 JWT 密钥不一致 | 检查两边的 `FLOWFORGE_JWT_SECRET` |
| 注册返回 `U1001` 但用户名没重复 | 上一次注册已成功 | 换用户名，或直接登录 |
| 建表脚本改了但没生效 | 初始化脚本只在数据卷为空时执行 | `docker compose down -v` 后重启 |
| Nacos 注册失败 403 | 3.x 默认开启鉴权 | 确认 `NACOS_USERNAME` / `NACOS_PASSWORD` 正确 |
