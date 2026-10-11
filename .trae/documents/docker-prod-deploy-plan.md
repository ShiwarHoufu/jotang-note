# Docker 生产打包部署方案

## Context

项目需要对阿里云 ECS 做 Docker 打包部署。现状：配置已全环境变量化（application.yaml），已有开发用 docker-compose.yml（仅 RabbitMQ），无 Dockerfile。用户已拍板：

1. **全容器化**：生产 compose 一次起 app + MySQL 8.4 + Redis + RabbitMQ，数据落命名卷
2. **服务器上多阶段构建**：git pull → `docker compose -f docker-compose.prod.yml up -d --build` 一条命令完成
3. 不动现有开发 compose；本次不做 Nginx/前端（代码尚不存在），app 端口只绑 127.0.0.1 为反代留路

## 文件清单

| 动作 | 文件 | 说明 |
|---|---|---|
| 新增 | `Dockerfile` | 多阶段：Maven 构建 + JRE 运行 |
| 新增 | `docker-compose.prod.yml` | 生产四件套，`name: jotang-prod` 与 dev 隔离 |
| 新增 | `.dockerignore` | **关键：排除 src/main/resources/application-local.yaml（含本地明文密钥，不排除会打进 jar）** |
| 修改 | `.env.example` | 追加生产变量段（保留现有 RABBITMQ 两项） |

现有 `docker-compose.yml`、`application-local.yaml`、`.gitignore` 零改动。

## 1. Dockerfile

关键决策：
- 构建阶段 `maven:3.9-eclipse-temurin-21`——**不依赖 mvnw**（wrapper jar 不进 git），镜像自带 Maven；BuildKit cache mount 缓存 ~/.m2，二次构建只下增量
- 先 COPY pom.xml 预拉依赖再 COPY src（pom 不变时命中层缓存）
- `-DskipTests`：surefire 不执行，pom 里 mockito javaagent 的 argLine 无从生效（已验证 ${settings.localRepository} 插值必然成功，且无消费者）
- 运行阶段 `eclipse-temurin:21-jre-noble`（自带 bash、无 curl）：healthcheck 用 bash 内建 `/dev/tcp` 探 8080，不装 curl
- 非 root 用户（uid 1001）+ 预建 `/app/uploads` 并 chown——命名卷首挂继承镜像内属主，不预建则 100MB multipart 中转写入 Permission denied
- `COPY --from=build /build/target/*.jar app.jar`：规避 finalName 硬编码（repackage 后 original 后缀为 .jar.original，不会误匹配）

```dockerfile
# syntax=docker/dockerfile:1
# ---------- 构建阶段：镜像自带 Maven，不依赖 mvnw（wrapper jar 不进 git） ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# 先只拷 pom 预拉依赖：pom 不变时该层直接命中缓存
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp -DskipTests dependency:go-offline

COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp -DskipTests package

# ---------- 运行阶段 ----------
FROM eclipse-temurin:21-jre-noble
ENV TZ=Asia/Shanghai
WORKDIR /app

# 预建上传中转目录并授权：命名卷首次挂载继承镜像内属主
RUN useradd --system --uid 1001 --create-home app \
 && mkdir -p /app/uploads \
 && chown -R app:app /app
USER app

COPY --from=build /build/target/*.jar app.jar

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"
ENTRYPOINT ["sh", "-c", "exec java -Duser.timezone=Asia/Shanghai $JAVA_OPTS -jar app.jar"]

HEALTHCHECK --interval=15s --timeout=5s --start-period=90s --retries=5 \
  CMD bash -c '</dev/tcp/127.0.0.1/8080' || exit 1
```

## 2. docker-compose.prod.yml

关键决策：
- 顶级 `name: jotang-prod` + 卷 `jotang-prod-` 前缀，与 dev compose（显式卷 rabbitmq-data）零交集
- MySQL 用专用账号（MYSQL_USER/MYSQL_PASSWORD 自动建号授 ALL 权限，覆盖 sql.init 建表所需 DDL），不用 root 连应用；`MYSQL_DATABASE` 与 app `DB_NAME` 引用同一变量
- MySQL healthcheck 用镜像自带 `healthcheck.sh --connect --innodb_initialized`（8.0.29+）
- Redis 密码走 `requirepass`，healthcheck 用 `REDISCLI_AUTH` 环境变量（redis-cli 原生认，无 -a 明文告警）
- 端口发布面只有两条：`app → 127.0.0.1:8080`、`rabbitmq 管理台 → 127.0.0.1:15672`（SSH 隧道访问）；MySQL/Redis 不发布，叠加安全组只开 22
- 必填无默认值的变量（JWT_SECRET、OSS 两个 Key）用 `:?` 校验，缺失在部署期报错而非运行期启动失败
- `JOTANG_MAIL_MODE` 生产默认 `queue`（RabbitMQ 已在拓扑内，呼应 application.yaml 里「部署时可能忘了翻成 queue」的警告）
- `JOTANG_UPLOAD_TMP=/app/uploads` 落专用卷；每服务配 json-file 日志轮转（YAML anchor 复用）
- app `mem_limit: 768m` + MaxRAMPercentage=75 → 堆约 576m；全套内存预算 ≈2GB（2GB ECS 需 swap 兜底，4GB 从容）

```yaml
name: jotang-prod

services:
  mysql:
    image: mysql:8.4
    restart: unless-stopped
    environment:
      TZ: Asia/Shanghai
      MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD:?缺少 MYSQL_ROOT_PASSWORD}
      MYSQL_DATABASE: ${DB_NAME:-jotang_note}
      MYSQL_USER: ${MYSQL_USER:?缺少 MYSQL_USER}
      MYSQL_PASSWORD: ${MYSQL_PASSWORD:?缺少 MYSQL_PASSWORD}
    command:
      - --character-set-server=utf8mb4
      - --collation-server=utf8mb4_0900_ai_ci
    volumes:
      - jotang-prod-mysql-data:/var/lib/mysql
    healthcheck:
      test: ["CMD", "healthcheck.sh", "--connect", "--innodb_initialized"]
      interval: 10s
      timeout: 5s
      retries: 10
      start_period: 60s
    logging: &logging
      driver: json-file
      options: { max-size: "10m", max-file: "3" }

  redis:
    image: redis:8-alpine
    restart: unless-stopped
    environment:
      REDIS_PASSWORD: ${REDIS_PASSWORD:?缺少 REDIS_PASSWORD}
      REDISCLI_AUTH: ${REDIS_PASSWORD}
    command: ["sh", "-c", "exec redis-server --requirepass \"$REDIS_PASSWORD\""]
    volumes:
      - jotang-prod-redis-data:/data
    healthcheck:
      test: ["CMD-SHELL", "redis-cli ping | grep -q PONG"]
      interval: 10s
      timeout: 3s
      retries: 5
    logging: *logging

  rabbitmq:
    image: rabbitmq:4-management
    restart: unless-stopped
    environment:
      RABBITMQ_DEFAULT_USER: ${RABBITMQ_USER:-admin}
      RABBITMQ_DEFAULT_PASS: ${RABBITMQ_PASSWORD:?缺少 RABBITMQ_PASSWORD}
    volumes:
      - jotang-prod-rabbitmq-data:/var/lib/rabbitmq
    ports:
      - "127.0.0.1:15672:15672"
    healthcheck:
      test: ["CMD", "rabbitmq-diagnostics", "-q", "ping"]
      interval: 10s
      timeout: 5s
      retries: 5
      start_period: 40s
    logging: *logging

  app:
    build: .
    image: jotang-note:latest
    restart: unless-stopped
    depends_on:
      mysql:
        condition: service_healthy
      redis:
        condition: service_healthy
      rabbitmq:
        condition: service_healthy
    environment:
      TZ: Asia/Shanghai
      DB_HOST: mysql
      DB_PORT: "3306"
      DB_NAME: ${DB_NAME:-jotang_note}
      DB_USERNAME: ${MYSQL_USER}
      DB_PASSWORD: ${MYSQL_PASSWORD}
      REDIS_HOST: redis
      REDIS_PORT: "6379"
      REDIS_PASSWORD: ${REDIS_PASSWORD}
      RABBITMQ_HOST: rabbitmq
      RABBITMQ_PORT: "5672"
      RABBITMQ_USER: ${RABBITMQ_USER:-admin}
      RABBITMQ_PASSWORD: ${RABBITMQ_PASSWORD}
      JWT_SECRET: ${JWT_SECRET:?缺少 JWT_SECRET}
      JOTANG_OSS_ACCESS_KEY_ID: ${JOTANG_OSS_ACCESS_KEY_ID:?缺少 OSS AccessKey}
      JOTANG_OSS_ACCESS_KEY_SECRET: ${JOTANG_OSS_ACCESS_KEY_SECRET:?缺少 OSS Secret}
      JWT_TTL: ${JWT_TTL:-2h}
      JOTANG_OSS_ENDPOINT_SERVER: ${JOTANG_OSS_ENDPOINT_SERVER:-oss-cn-chengdu-internal.aliyuncs.com}
      JOTANG_OSS_ENDPOINT_BROWSER: ${JOTANG_OSS_ENDPOINT_BROWSER:-oss-cn-chengdu.aliyuncs.com}
      JOTANG_OSS_BUCKET_NOTE: ${JOTANG_OSS_BUCKET_NOTE:-jotang-note}
      JOTANG_OSS_BUCKET_AVATAR: ${JOTANG_OSS_BUCKET_AVATAR:-jotang-avatar}
      JOTANG_MAIL_HOST: ${JOTANG_MAIL_HOST:-smtp.qq.com}
      JOTANG_MAIL_PORT: ${JOTANG_MAIL_PORT:-465}
      JOTANG_MAIL_USERNAME: ${JOTANG_MAIL_USERNAME:-}
      JOTANG_MAIL_PASSWORD: ${JOTANG_MAIL_PASSWORD:-}
      JOTANG_MAIL_FROM: ${JOTANG_MAIL_FROM:-}
      JOTANG_MAIL_RESET_LINK_BASE: ${JOTANG_MAIL_RESET_LINK_BASE:-http://localhost:5173/reset-password}
      JOTANG_MAIL_MODE: ${JOTANG_MAIL_MODE:-queue}
      JOTANG_UPLOAD_TMP: /app/uploads
    ports:
      - "127.0.0.1:8080:8080"
    volumes:
      - jotang-prod-uploads:/app/uploads
    mem_limit: 768m
    logging: *logging

volumes:
  jotang-prod-mysql-data:
  jotang-prod-redis-data:
  jotang-prod-rabbitmq-data:
  jotang-prod-uploads:
```

## 3. .dockerignore

```
.git
.idea
.vscode
target/
.mvn
mvnw
mvnw.cmd
HELP.md
.env
.env.*
**/application-local.yaml
docs
*.md
docker-compose.yml
docker-compose.prod.yml
Dockerfile
.dockerignore
```

## 4. .env.example 追加生产段

保留现有 RABBITMQ 两项，追加：

```bash
# ===== 以下为生产部署（docker-compose.prod.yml）所需，本地开发可忽略 =====
# 密码生成：openssl rand -base64 24；JWT_SECRET 必须 openssl rand -base64 32
DB_NAME=jotang_note
MYSQL_USER=jotang
MYSQL_PASSWORD=
MYSQL_ROOT_PASSWORD=
REDIS_PASSWORD=
JWT_SECRET=
JOTANG_OSS_ACCESS_KEY_ID=
JOTANG_OSS_ACCESS_KEY_SECRET=
JOTANG_OSS_ENDPOINT_SERVER=oss-cn-chengdu-internal.aliyuncs.com
JOTANG_OSS_ENDPOINT_BROWSER=oss-cn-chengdu.aliyuncs.com
JOTANG_OSS_BUCKET_NOTE=jotang-note
JOTANG_OSS_BUCKET_AVATAR=jotang-avatar
JOTANG_MAIL_HOST=smtp.qq.com
JOTANG_MAIL_PORT=465
JOTANG_MAIL_USERNAME=
JOTANG_MAIL_PASSWORD=
JOTANG_MAIL_FROM=
JOTANG_MAIL_RESET_LINK_BASE=http://<ECS_IP>/reset-password
JOTANG_MAIL_MODE=queue
```

## 5. ECS 操作步骤（写入最终答复，不建文档文件）

```bash
# 安装 Docker（若未装）
curl -fsSL https://get.docker.com | sh

# 2GB 机器先加 swap（构建期 Maven 兜底）
sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile \
  && sudo mkswap /swapfile && sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab

# 首次部署
git clone <repo> && cd jotang-note
cp .env.example .env && vim .env   # 填真实值；:? 项漏填会在 up 时报错
docker compose -f docker-compose.prod.yml up -d --build
docker compose -f docker-compose.prod.yml logs -f app

# 日常更新
git pull && docker compose -f docker-compose.prod.yml up -d --build
```

## 验证步骤

1. 本地：`docker build -t jotang-note:local .` 构建通过，第二次构建明显变快（cache mount 生效）
2. `docker compose -f docker-compose.prod.yml config` 语法与插值校验
3. `up -d --build` 后：四服务 ps 均 healthy；app 日志出现「邮件投递方式：RabbitMQ 队列」「重置邮件消费者已启动」横幅（验证 queue 模式生效）；`curl http://127.0.0.1:8080/` 返回 401/404 即 Tomcat 活着
4. `restart app` 验证 sql.init 幂等；`exec app ls -ld /app/uploads` 属主为 app
5. `down`（不带 -v）后重启，验证命名卷数据保留
6. 本地 Docker Desktop 默认启用 BuildKit，cache mount 与 127.0.0.1 绑定均可用

## 风险提示（执行时注意）

- .env 是 compose 变量唯一来源；Spring Boot 不读 .env，但生产走 compose environment 注入，无此坑
- Redis 密码会出现在 command 明文里（docker inspect 可见），单机自用可接受
- 概要设计 §1.2 原写「MySQL 装在 ECS 原生」——本次全容器化与之有出入，文档是否同步更新由用户另行决定，不在本次范围内
