# 前端镜像：node 构建静态产物，nginx 托管并反代 /api 到后端
FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
# Vite 在构建期把这些烙进产物；由 docker-compose 从 .env 传入
ARG VITE_CASDOOR_BASE_URL
ARG VITE_CASDOOR_REDIRECT_URI
ARG VITE_CASDOOR_CLIENT_ID=xyyx-web
ENV VITE_CASDOOR_BASE_URL=${VITE_CASDOOR_BASE_URL} \
    VITE_CASDOOR_REDIRECT_URI=${VITE_CASDOOR_REDIRECT_URI} \
    VITE_CASDOOR_CLIENT_ID=${VITE_CASDOOR_CLIENT_ID}
RUN npm run build

FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
