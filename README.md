# EICS — Enterprise Integrated Customer Service System

企业一体化智能客服系统，纯 Java 技术栈，私有化部署。

- 🤖 **RAG 知识库问答**：Milvus 向量检索 + LLM 生成答案，附带文档溯源
- 📋 **多轮对话工单**：状态机驱动自动收集信息，LLM 增强实体提取
- 🔁 **AI 转人工坐席**：无缝转接 + WebSocket 实时聊天 + 会话留存
- 📊 **企业级管理后台**：工作台 / 会话管理 / 工单管理 / 知识库管理 / 系统设置
- ⭐ **满意度评价**：1-5 星评分，实时推送 + 补偿拉取
- 🔔 **SLA 超时告警**：WebSocket 实时推送，桌面通知
- 👥 **多角色用户系统**：USER / AGENT / ADMIN，JWT 认证

---

## 技术栈

| 层 | 技术 |
|------|------|
| 后端 | Java 17 + Spring Boot 3.3 + Spring AI 1.0 |
| 对话引擎 | Java 状态机（主）+ LLM 增强实体提取 |
| ORM | MyBatis-Plus + MySQL 8.0 |
| 缓存 | Redis 7 |
| 向量库 | Milvus 2.4 (HNSW) |
| 文件存储 | MinIO |
| 前端 | Vue 3 + Element Plus + Vite |
| LLM | 阿里云 DashScope (Qwen-Max) |
| 部署 | Docker Compose 一键编排 |

---

## 快速启动

### 前置条件

- JDK 17+
- Node.js 18+
- Docker 24+ (含 Compose v2)
- 阿里云 DashScope API Key

### 1. 启动中间件

```bash
cd docker
cp .env.example .env          # 编辑 .env 填入 API Key 和密码
docker compose up -d
```

### 2. 启动后端

```bash
cd eics-server
mvn clean package -DskipTests
java -jar target/eics-server-v2-2.0.0-SNAPSHOT.jar
```

### 3. 启动前端

```bash
cd eics-web
npm install
npm run dev
```

### 4. 访问

| 页面 | 地址 |
|------|------|
| 聊天页面 | http://localhost:3000 |
| 管理后台 | http://localhost:3000/admin/dashboard |
| API 文档 | http://localhost:8080/swagger-ui.html |

> 默认管理员：`admin` / `admin123` | 默认用户：`testuser` / `admin123`

---

## 项目结构

```
├── docker/                     # Docker Compose 编排 + SQL 初始化
├── eics-server/                # Java 后端
│   └── src/main/java/com/eics/
│       ├── dialog/             # 对话引擎（IntentRouter + FormStateMachine + EntityExtractor）
│       ├── controller/         # REST 接口
│       ├── service/            # 业务服务
│       ├── websocket/          # WebSocket 消息网关
│       └── entity/             # 数据实体
├── eics-web/                   # Vue 3 前端
│   └── src/
│       ├── views/admin/        # 管理后台（5 页面 + 工作台）
│       ├── layouts/            # AdminLayout
│       └── components/         # 通用组件（7 个）
└── docs/                       # 项目文档（7 份）
```

---

## 文档

| 文档 | 说明 |
|------|------|
| [部署手册](docs/01-部署手册.md) | 环境要求、安装步骤、配置说明 |
| [用户手册](docs/02-用户手册.md) | 聊天、工单、评价操作指南 |
| [管理员手册](docs/03-管理员手册.md) | 用户管理、工单、知识库、快捷回复 |
| [运维手册](docs/04-运维手册.md) | 备份恢复、故障排查、性能优化 |
| [需求规格说明书](docs/05-需求规格说明书.md) | 30 条功能需求 + 非功能需求 |
| [系统架构设计说明书](docs/06-系统架构设计说明书.md) | 架构图、模块划分、安全设计 |
| [数据库设计说明书](docs/07-数据库设计说明书.md) | ER 图、表结构、索引策略 |

---

## 环境变量

| 变量 | 必填 | 说明 |
|------|------|------|
| `DASHSCOPE_API_KEY` | ✅ | 阿里云 DashScope API Key |
| `JWT_SECRET` | ✅ | JWT 签名密钥（生产环境务必修改） |
| `MYSQL_ROOT_PASSWORD` | ✅ | MySQL root 密码 |
| `REDIS_PASSWORD` | ✅ | Redis 密码 |
| `MINIO_ROOT_PASSWORD` | ✅ | MinIO 管理员密码 |

详见 `docker/.env.example`。

---

## 生产部署

完整步骤参见 [部署手册](docs/01-部署手册.md)，包含服务器初始化、Docker 安装、防火墙配置、LLM 配置等。

---

## License

MIT
