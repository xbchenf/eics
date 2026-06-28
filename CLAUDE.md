# EICS V2.0 — 纯 Java 版企业一体化智能客服系统

> 基于 V1.0 实践经验 + `docs/EICS-v2-Java方案报告.md` 的全 Java 技术栈重构。

---

## 一、V1.0 → V2.0 核心变更

V1.0 架构问题：
- Python/Rasa 与 Java 异构部署
- Rasa 3.6 开源版已停更
- DIET 模型每次加意图需重新标注→训练→评估
- NLU 格式迁移成本高（Markdown→YAML、FormAction→声明式）
- 环境问题频发（jieba 安装、Python 版本限定、pip 源不可用）

**V2.0 解决方案：状态机主导 + LLM 增强实体提取，替代 Rasa。**

| 能力 | V1.0 (Rasa) | V2.0 (纯 Java) |
|------|-----------|----------------|
| 意图识别 | Rasa DIET (需训练) | **关键词 + LLM 辅助分类** |
| 实体提取 | DIET + RegexEntityExtractor | **LLM 提取（可选）→ 正则兜底** |
| 多轮填槽 | Rasa FormAction | **Java 状态机主导 + LLM 增强** |
| RAG 问答 | SpringAI RAG | 不变 |
| 文档解析 | Tika | 不变 |
| 向量存储 | Milvus | 不变 |
| 坐席/工单 | Java | 不变 |
| 部署 | Docker × 3 服务 | **Docker × 1 服务** |
| 代码语言 | Java + Python | **纯 Java** |

---

## 二、技术栈

| 层 | 技术 |
|----|------|
| 框架 | **Java 17** + Spring Boot 3.3.x |
| AI 编排 | Spring AI 1.0 GA（ChatClient + Tool Calling + VectorStore 统一 API） |
| 对话引擎 | **Java 状态机（主）+ LLM 增强实体提取** |
| RAG | Spring AI + Milvus 2.4 |
| LLM | Qwen-Max / DeepSeek-V3（DashScope OpenAI 兼容协议） |
| 持久层 | MyBatis-Plus、MySQL 8.0 |
| 缓存/会话 | Redis（对话上下文 + 在线状态） |
| 文件服务 | MinIO |
| 前端 | Vue 3 + Element Plus（**复用 V1.0 前端，接口兼容**） |
| 部署 | Docker Compose（**纯 Java，一个 Dockerfile**） |

---

## 三、架构分层

```
┌─────────────────────────────────────────┐
│              Vue 3 前端                   │
│   ChatView  │  AdminLayout (5 页面)      │
└──────────────┬──────────────────────────┘
               │ REST + WebSocket
┌──────────────▼──────────────────────────┐
│        Spring Boot 3.3 (Java 17)        │
│                                          │
│  ┌────────────┐  ┌────────────────────┐ │
│  │ RAG 模块   │  │ 对话引擎 (新核心)   │ │
│  │ - 文档解析 │  │ - DialogueEngine   │ │
│  │ - 向量检索 │  │ - FormStateMachine │ │
│  │ - LLM 生成 │  │ - IntentTools      │ │
│  └────────────┘  └────────────────────┘ │
│                                          │
│  ┌────────────┐  ┌────────────────────┐ │
│  │ 工单模块   │  │ 坐席模块           │ │
│  └────────────┘  └────────────────────┘ │
└──────────────┬──────────────────────────┘
               │
┌──────────────▼──────────────────────────┐
│    中间件: MySQL / Redis / Milvus / MinIO │
└─────────────────────────────────────────┘
```

---

## 四、对话引擎设计：状态机主导 + LLM 增强

### 4.1 为什么不用纯 LLM Function Calling

纯 LLM 方案存在三个致命风险：

1. **延迟不可控**：每次对话都要调 LLM（1-3 秒），"输入手机号→填槽→下一个问题"本来 <1ms 的操作被放大
2. **输出不稳定**：LLM 可能给手机号填成"好的，我的手机是 138xxxx"而不是纯数字
3. **对话状态丢失**：LLM 无状态，需要把完整对话历史塞进 Context，Token 消耗线性增长

**正确架构**：

```
用户消息
   ↓
状态机：查 Redis 当前状态（如 COLLECTING_PHONE），确定性操作，<1ms
   ↓
LLM 增强（可选）：从用户输入中提取实体，如 "手机号是13800138000" → phone=13800138000
   ↓                               ↑ 超时 2s 或失败
正则兜底：re.search(r"1[3-9]\d{9}", text) ← 降级路径
   ↓
填槽 + 推进状态 → Redis 写入新状态 → 返回追问
```

| 对比 | 纯 LLM | 状态机主导 + LLM 增强 |
|------|--------|----------------------|
| 意图路由 | LLM 决策（不稳定） | 关键词匹配（确定） |
| 槽位控制 | LLM 记忆（丢失风险） | Redis 状态（持久可靠） |
| 实体提取 | LLM 结构化输出 | LLM 尝试 → 正则兜底 |
| LLM 超时 | ❌ 阻塞 | ✅ 跳过 LLM，正则接盘 |
| Token 消耗 | 高（全对话历史） | 低（仅当前消息） |
| 运维复杂度 | 调 Prompt | 改代码逻辑 |

### 4.2 意图路由：关键词优先 + LLM 兜底

```java
@Component
public class IntentRouter {

    // 确定性规则（优先）
    private static final Map<String, String> KEYWORD_INTENTS = Map.of(
        "转人工", "request_human",
        "人工", "request_human",
        "工单", "ticket_create",
        "报修", "ticket_create",
        "提交工单", "ticket_create"
    );

    public String resolve(String userMessage) {
        // 1. 关键词精确匹配（<1ms，零成本）
        for (var entry : KEYWORD_INTENTS.entrySet()) {
            if (userMessage.contains(entry.getKey())) return entry.getValue();
        }
        // 2. LLM 辅助分类（仅关键词未命中时使用）
        try {
            return llmClassify(userMessage);  // 超时 2s
        } catch (Exception e) {
            return "faq_ask";  // 默认走 RAG 问答
        }
    }
}
```

### 4.3 状态机：确定性填槽流程

```java
@Component
public class FormStateMachine {

    private final StringRedisTemplate redis;

    // 状态枚举
    public enum State { IDLE, COLLECTING_PHONE, COLLECTING_ISSUE_TYPE, COLLECTING_DESC }

    // 槽位存储在 Redis Hash: dialog:{sessionId}:slots
    // 状态存储在 Redis String: dialog:{sessionId}:state

    public String handle(String sessionId, String userMessage, String intent) {
        State state = getState(sessionId);
        Map<String, String> slots = getSlots(sessionId);

        // 工单意图 → 启动表单
        if ("ticket_create".equals(intent) && state == State.IDLE) {
            setState(sessionId, State.COLLECTING_PHONE);
            return "请输入您的手机号，方便我们联系您：";
        }

        // 表单流程中 → 提取实体 → 填槽 → 推进
        String extracted = extractEntity(userMessage, state);
        if (extracted != null) {
            slots.put(state.slotName(), extracted);
            setSlots(sessionId, slots);
            State next = advanceState(state);
            setState(sessionId, next);
            return askForSlot(next, slots);
        }
        // 提取失败 → 重新追问
        return retryAsk(state);
    }

    // 实体提取：LLM 尝试 → 正则兜底
    private String extractEntity(String text, State state) {
        return switch (state) {
            case COLLECTING_PHONE -> extractPhone(text);
            case COLLECTING_ISSUE_TYPE -> extractIssueType(text);
            case COLLECTING_DESC -> text;  // 故障描述直接取原文
            default -> null;
        };
    }

    private String extractPhone(String text) {
        // 1. 正则优先（确定，零延迟）
        var m = Pattern.compile("1[3-9]\\d{9}").matcher(text);
        if (m.find()) return m.group();
        // 2. 纯数字 11 位
        if (text.trim().matches("\\d{11}")) return text.trim();
        // 3. LLM 尝试（可选，有超时降级）
        try {
            return llmExtract(text, "phone");
        } catch (Exception e) {
            return null;  // 失败返回 null → 状态机重新追问
        }
    }
}
```

### 4.4 设计要点

1. **状态机永远正确**：即使 LLM 挂了，正则仍然能提取手机号；即使正则没匹配，状态机也能追问第二轮
2. **LLM 是增强不是替代**：LLM 擅长从"我的手机号是 138xxxx"中提取，正则从纯数字中提取，两者互补
3. **每步独立决策**：不把整个对话上下文扔给 LLM，只把当前用户消息 + 当前槽位类型发给 LLM，Token 消耗极小
4. **对话状态在 Redis**：服务器重启不丢状态，30 分钟超时自动清理

---

## 五、新增/删除组件

### 删掉（V1.0 残留）
| 组件 | 原因 |
|------|------|
| `eics-rasa/` 整个目录 | LLM 替代 |
| `RasaClient.java` | 不再调 Rasa HTTP |
| `actions.py` | Function Calling 替代 |
| `config.yml / domain.yml / nlu.yml / rules.yml` | 不再需要训练配置 |
| `models/*.tar.gz` | 不再需要 NLU 模型文件 |
| `start.sh` | 不再需要 Rasa 启动脚本 |
| Rasa Docker 镜像 + 容器 | 不再需要 |

### 新增
| 组件 | 用途 |
|------|------|
| `IntentRouter.java` | 意图路由（关键词优先 + LLM 兜底） |
| `FormStateMachine.java` | 多轮表单状态机（Redis 持久化） |
| `EntityExtractor.java` | 实体提取（LLM 尝试 + 正则兜底） |
| `DialogController.java` | 对话 REST API（替代 Rasa webhook） |
| `dialog-cache.yml` | 对话状态 Redis 缓存配置 |

---

## 六、API 路径（保持 V1.0 兼容）

| 前缀 | 说明 | 变更 |
|------|------|------|
| `/api/v1/rag/**` | RAG 问答 | 复用 |
| `/api/v1/order/**` | 工单管理 | 复用 |
| `/api/v1/agent/**` | 坐席管理 | 复用 |
| `/api/v1/session/**` | 会话管理 | 复用 |
| `/api/v1/doc/**` | 知识库文档 | 复用 |
| `/api/v1/auth/**` | 认证 | 复用 |
| `/api/v1/dialog/**` | 对话管理 | **新增** |
| `/ws/chat/**` | WebSocket 消息 | 重构（不再调 Rasa） |

> **前端兼容**：API 路径、请求/响应格式保持 V1.0 一致，ChatView 和 AdminView 无需改动。

---

## 七、成本分析

| 场景 | Token/次 | 单价 (Qwen-Max) | 单次成本 |
|------|---------|-----------------|---------|
| FAQ 问答 | ~800 tokens | ¥0.04/1K | ¥0.032 |
| 工单提交（4轮对话） | ~2000 tokens | ¥0.04/1K | ¥0.08 |
| 简单问候 | ~200 tokens | ¥0.04/1K | ¥0.008 |

月均 1000 次对话 ≈ **¥50-100**。

---

## 八、风险与对策

| 风险 | 对策 |
|------|------|
| LLM 输出不稳定（填错槽位） | 正则兜底提取，状态机确保流程完整 |
| LLM API 超时 | 3 次重试 + 降级话术 |
| 对话成本不可控 | `max-tokens` 限制 + 缓存常见 FAQ 答案 |
| 内网无法调 LLM | 部署私有 Qwen / 蒸馏小模型备选 |
| 知识库幻觉（编造工单号） | Tool Calling 强制调 API，LLM 不生成编号 |

---

## 九、开发阶段

### 阶段 1：状态机 + 意图路由 (2天)
- 实现 `FormStateMachine.java`（状态枚举 + Redis 持久化 + 超时清理）
- 实现 `IntentRouter.java`（关键词匹配 + LLM 分类兜底）
- 实现 `EntityExtractor.java`（正则提取 phone/issue_type + LLM 增强）
- 单元测试：逐个状态 + 边界条件

### 阶段 2：对话 API + LLM 集成 (2天)
- 实现 `DialogController.java`（REST 端点，替代 Rasa webhook）
- LLM 实体提取集成（带 2s 超时 + 降级）
- 删除 Rasa 依赖（`RasaClient.java`, pom.xml 相关配置）

### 阶段 3：WebSocket 重构 (1天)
- 重构 `ChatWebSocketHandler`：不再调 Rasa，改为调 `DialogueEngine`
- 消息格式保持兼容

### 阶段 4：前端适配 (1天)
- 删除 `sendToRasa` 等废弃 API 调用
- 按钮快捷回复（问题类型选择已实现，确认兼容）

### 阶段 5：测试与部署 (2天)
- 端到端测试 3 条核心链路
- 简化 Docker Compose（移除 Rasa 服务）
- 更新 README

**总计：约 9 个工作日**

---

## 十、编码规范

- 遵循阿里开发手册，统一返回体 `Result<T>`
- 基础包名：`com.eics`
- 新增 `com.eics.dialog` 包——IntentRouter + FormStateMachine + EntityExtractor
- LLM Prompt 模板放在 `resources/prompts/` 目录
- 禁止硬编码话术——回复文本统一在配置或常量类中定义
- 数据库表完全复用 V1.0，无需新增
