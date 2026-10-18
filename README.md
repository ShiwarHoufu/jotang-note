# Jotang Note

Jotang Note 是面向学生的课程笔记托管与分享平台。

网站：[Jotang Note](http://47.109.133.215)\
个人学习笔记仓库：[学习笔记](https://github.com/ShiwarHoufu/notes)

## 功能一览

- **账号**：注册（需选择学院）、登录、退出、昵称/头像、密码找回（邮件）
- **笔记**：单文件上传（图片 / Markdown / 文本）、编辑元数据、删除
- **发现**：首页最新与热门；课程、标签、关键词搜索
- **阅读**：图片与 Markdown / 文本在线预览
- **收藏与主页**：收藏 / 取消收藏、我的收藏、我的上传、个人主页
- **管理**：管理员下架 / 恢复笔记（无独立后台，详情页直接操作）
- **AI**：
  - 上传 / 编辑时点击“自动生成摘要”
  - 对话与问答：模型可按标题检索在线笔记并读取正文

## 技术栈

| 分层 | 选型                                                                 |
| -- | ------------------------------------------------------------------ |
| 后端 | Java 21 · Spring Boot 4.0.8 · Spring Security + JWT · MyBatis-Plus |
| 前端 | Vue 3 · Vite · TypeScript · Element Plus · Pinia · Vue Router      |
| 数据 | MySQL 8 · Redis 8（笔记详情缓存）                                          |
| 消息 | RabbitMQ 4（密码找回邮件异步投递、孤儿对象补偿删除）                                    |
| 存储 | 阿里云 OSS（笔记私有 Bucket + 头像公共读 Bucket）                                |
| AI | Spring AI 2.0.1 + DeepSeek（OpenAI 兼容接口）                            |
| 部署 | Nginx + 可执行 Jar（systemd）+ Docker Compose 中间件                       |

## 目录结构

```
jotang-note/
├── src/main/java/com/wlf/       # 后端源码，按业务模块分包
├── src/main/resources/
│   ├── application.yaml         # 主配置（占位符从环境变量读取）
│   ├── application-local.yaml   # 本地敏感配置，已被 gitignore
│   ├── schema.sql 、 data.sql    # 建表与种子数据，启动时幂等执行
│   └── mapper/                  # MyBatis XML
├── src/test/java/com/wlf/       # 测试；oss / mail / mq 用例默认排除
├── frontend/                    # Vue 3 前端
├── deploy/                      # Nginx、systemd、生产 Compose、update.sh
├── docs/                        # 需求、技术选型、概要设计、部署运维、功能说明
└── docker-compose.yml           # 本地开发用的 RabbitMQ + Redis
```

## 本地启动

### 环境要求

- JDK 21
- Node.js 22+
- MySQL 8
- Docker（启动 RabbitMQ 与 Redis）

### 1. 启动中间件

```bash
cp .env.example .env          # 填 RabbitMQ 密码
docker compose up -d          # 只起 RabbitMQ(5672/15672) 与 Redis(6379)
```

我的 MySQL 本机已有实例。故本地开发用的 compose 故意不含 MySQL，避免与已有实例抢端口。

### 2. 准备本地配置

也可以不建文件，改用环境变量注入，变量名见 [`application.yaml`](src/main/resources/application.yaml)

仓库不会提交任何密码与 AccessKey。新建 `src/main/resources/application-local.yaml`（已被 `.gitignore` 忽略），至少填写：

```yaml
spring:
  datasource:
    username: root
    password: <数据库密码>
  rabbitmq:
    password: <与 .env 中 RABBITMQ_PASSWORD 一致>

DB_PORT: 

jwt:
  secret: <Base64 编码、解码后至少 256 位的随机串>

aliyun:
  oss:
    access-key-id: <OSS AccessKey>
    access-key-secret: <OSS Secret>
    # 本机不在阿里云内网，必须用公网 Endpoint
    endpoint-server: oss-cn-chengdu.aliyuncs.com
    bucket-note: <开发用 Bucket>
    bucket-avatar: <开发用 Bucket>

# 可留空，留空时仅 AI 调用失败（50300），不影响启动
JOTANG_AI_API_KEY: <DeepSeek API Key>
```

也可以不建文件，改用环境变量注入，变量名见 [`application.yaml`](src/main/resources/application.yaml)

### 3. 启动后端

首次启动会自动执行 `schema.sql` 与 `data.sql` 建表并写入学院、课程、标签种子数据。

> 管理员账号需手工创建

### 4. 启动前端

```bash
cd frontend
npm install
npm run dev
```

## 测试

```bash
./mvnw test
```

依赖外部环境的用例默认被排除，需要时单独运行（运行前须清空排除项，否则一个都不会跑）：

```bash
./mvnw test -DexcludedGroups= -Dgroups=oss    # 真连阿里云 OSS
./mvnw test -DexcludedGroups= -Dgroups=mail   # 真发一封邮件
./mvnw test -DexcludedGroups= -Dgroups=mq     # 真连 RabbitMQ
```

