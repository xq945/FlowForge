-- FlowForge 数据库与账号初始化
-- 注意：仅在数据卷为空时执行一次；修改后需要 docker compose down -v 重建
--
-- 按服务划分 schema，应用账号只被授权访问自己的库，
-- 这样「禁止跨服务直连对方表」不只是口头约定，而是有权限层面的约束。

CREATE DATABASE IF NOT EXISTS flowforge_user
    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- 本地开发专用账号；生产环境应由部署脚本单独注入强密码
CREATE USER IF NOT EXISTS 'flowforge'@'%' IDENTIFIED BY 'flowforge';

GRANT ALL PRIVILEGES ON flowforge_user.* TO 'flowforge'@'%';

FLUSH PRIVILEGES;
