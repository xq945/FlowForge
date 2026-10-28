# 前端生产镜像
#
# 与后端服务保持同一策略：**构建在宿主机完成**（npm run build），
# 镜像只负责托管静态资源。好处是不必在容器里重新拉一遍 npm 依赖，
# 镜像构建是秒级的，也避免网络受限环境下 node 基础镜像拉不下来的问题。
#
# 使用前请先执行： npm run build

FROM nginx:1.27-alpine

COPY dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
