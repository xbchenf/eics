# EICS V2.0 — Enterprise Integrated Customer Service System

纯 Java 版企业一体化智能客服系统，**完全移除 Rasa 依赖**，对话引擎改用 Java 状态机 + LLM 增强实体提取。

- 🤖 **RAG 知识库问答**：Milvus 向量检索 + LLM 生成答案，附带文档溯源
- 📋 **多轮对话工单**：Java 状态机自动收集信息，LLM 增强实体提取
- 🔁 **AI 转人工坐席**：无缝转接 + WebSocket 实时聊天
- 📊 **坐席管理后台**：工作台 / 会话 / 工单 / 知识库 / 坐席管理

---

## V1.0 vs V2.0

| | V1.0 | V2.0 |
|---|------|------|
| 对话引擎 | Rasa 3.6 (Python) | Java 状态机 + LLM |
| 部署服务 | 9 个（含 Rasa） | **8 个** |
| 启动步骤 | 3 步（中间件+Rasa+Java） | **2 步** |
| 语言 | Java + Python | **纯 Java** |

---

## 快速启动

### 1. 启动中间件

```bash
cd docker
docker compose up -d
```

### 2. 启动 Java 后端（含对话引擎）

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
| 用户聊天 | http://localhost:3000 |
| 坐席登录 | http://localhost:3000/login |
| 坐席后台 | http://localhost:3000/admin/dashboard |
| API 文档 | http://localhost:8080/swagger-ui.html |

> 默认坐席账号：`admin` / `admin123`

---

## 环境变量

| 变量 | 说明 |
|------|------|
| `DASHSCOPE_API_KEY` | **必填**，阿里百炼 API Key |
| `JWT_SECRET` | JWT 签名密钥（生产环境务必修改） |
| 其他 | 参见 `docker/.env.example` |

---

## 项目结构

```
eics-server/          # Java 后端
├── dialog/           # 【新增】对话引擎（IntentRouter + FormStateMachine + EntityExtractor）
├── controller/       # REST 接口（含 DialogController）
├── service/          # RAG / 工单 / 坐席 / 文档
├── websocket/        # WebSocket 消息网关
└── ...

eics-web/             # Vue 3 前端
├── views/admin/      # 坐席后台（7 个页面）
├── components/       # 通用组件
└── ...

docker/               # Docker Compose（8 服务，无 Rasa）
```

---

## License

MIT
