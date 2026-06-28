package com.eics.websocket;

import com.eics.auth.JwtUtil;
import com.eics.entity.CsChatMessage;
import com.eics.entity.CsChatSession;
import com.eics.mapper.CsChatMessageMapper;
import com.eics.mapper.CsChatSessionMapper;
import com.eics.dialog.DialogService;
import com.eics.security.SensitiveWordFilter;
import com.eics.service.AgentService;
import com.eics.service.RAGService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * 统一消息网关 — 前端唯一入口
 *
 * 路由逻辑：
 *   session 不存在 / BOT 状态 → 调用 Rasa → 回复推回用户
 *   AGENT 状态 → 直接广播给房间内其他人（坐席↔用户）
 *
 * 安全：
 *   AGENT 类型消息需要携带有效 JWT Token（URL 查询参数 ?token=xxx）
 *   未认证连接只能以 USER 身份发送消息
 */
@Slf4j
@Component
public class ChatWebSocketHandler implements WebSocketHandler {

    private static final String ATTR_AGENT_ID = "agentId";
    private static final String ATTR_IS_AGENT = "isAgent";

    private final WebSocketSessionManager sessionManager;
    private final CsChatMessageMapper messageMapper;
    private final CsChatSessionMapper sessionMapper;
    private final DialogService dialogService;
    private final RAGService ragService;
    private final AgentService agentService;
    private final SensitiveWordFilter sensitiveWordFilter;
    private final ObjectMapper objectMapper;
    private final JwtUtil jwtUtil;

    public ChatWebSocketHandler(WebSocketSessionManager sessionManager,
                                CsChatMessageMapper messageMapper,
                                CsChatSessionMapper sessionMapper,
                                DialogService dialogService,
                                RAGService ragService,
                                AgentService agentService,
                                SensitiveWordFilter sensitiveWordFilter,
                                ObjectMapper objectMapper,
                                JwtUtil jwtUtil) {
        this.sessionManager = sessionManager;
        this.messageMapper = messageMapper;
        this.sessionMapper = sessionMapper;
        this.dialogService = dialogService;
        this.ragService = ragService;
        this.agentService = agentService;
        this.sensitiveWordFilter = sensitiveWordFilter;
        this.objectMapper = objectMapper;
        this.jwtUtil = jwtUtil;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession wsSession) {
        String sessionId = extractSessionId(wsSession);
        sessionManager.register(sessionId, wsSession);

        // 尝试从查询参数提取并验证 Token
        String token = extractQueryParam(wsSession, "token");
        if (token != null && !token.isEmpty()) {
            Map<String, Object> claims = jwtUtil.verifyToken(token);
            if (claims != null) {
                wsSession.getAttributes().put(ATTR_IS_AGENT, true);
                wsSession.getAttributes().put(ATTR_AGENT_ID, claims.get("agentId"));
                log.info("WebSocket 坐席连接: session={}, agentId={}", sessionId, claims.get("agentId"));
                return;
            }
        }
        // 匿名用户连接
        wsSession.getAttributes().put(ATTR_IS_AGENT, false);
        log.info("WebSocket 用户连接: session={}", sessionId);
    }

    @Override
    public void handleMessage(WebSocketSession wsSession, WebSocketMessage<?> message) {
        String sessionId = extractSessionId(wsSession);
        String payload = message.getPayload().toString();
        String content = extractContent(payload);
        if (content == null || content.isEmpty()) return;

        String senderType = extractSenderType(payload, "USER");

        if ("AGENT".equals(senderType)) {
            // 验证是否已认证为坐席
            Boolean isAgent = (Boolean) wsSession.getAttributes().get(ATTR_IS_AGENT);
            if (isAgent == null || !isAgent) {
                pushToSender(wsSession, Map.of(
                        "type", "error",
                        "content", "未授权：坐席操作需要登录认证"));
                return;
            }
            saveMessage(sessionId, "AGENT", content);
            sessionManager.broadcast(sessionId, wsSession.getId(), Map.of(
                    "type", "message",
                    "sender_type", "AGENT",
                    "content", content));
        } else {
            // 用户消息 — 确保会话存在且 user_id 已填充
            CsChatSession session = sessionMapper.selectById(sessionId);
            if (session == null) {
                session = new CsChatSession();
                session.setId(sessionId);
                session.setUserId(extractUserId(sessionId));
                session.setStatus("BOT");
                sessionMapper.insert(session);
            } else if (session.getUserId() == null || session.getUserId().isEmpty()) {
                session.setUserId(extractUserId(sessionId));
                sessionMapper.updateById(session);
            }
            boolean isBotMode = !"AGENT".equals(session.getStatus());

            if (isBotMode) {
                // V2.0: 先走对话引擎（工单/转人工等），引擎无回复时走 RAG 问答
                List<Map<String, Object>> replies = dialogService.processMessage(sessionId, content);
                if (!replies.isEmpty()) {
                    for (Map<String, Object> reply : replies) {
                        String replyText = (String) reply.getOrDefault("text", "");
                        if (!replyText.isEmpty()) {
                            saveMessage(sessionId, "BOT", replyText);
                            Map<String, Object> pushData = new java.util.HashMap<>();
                            pushData.put("type", "message");
                            pushData.put("sender_type", "BOT");
                            pushData.put("content", replyText);
                            if (reply.containsKey("buttons")) {
                                pushData.put("buttons", reply.get("buttons"));
                            }
                            pushToSender(wsSession, pushData);
                        }
                    }
                } else {
                    // 对话引擎无响应 → RAG 知识库问答
                    Map<String, Object> ragResult = ragService.chat(content, sessionId);
                    String answer = (String) ragResult.getOrDefault("answer", "");
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> sources = (List<Map<String, Object>>) ragResult.getOrDefault("sources", List.of());
                    if (!answer.isEmpty()) {
                        StringBuilder sb = new StringBuilder(answer);
                        if (!sources.isEmpty()) {
                            sb.append("\n\n📎 参考来源：\n");
                            for (int i = 0; i < Math.min(sources.size(), 3); i++) {
                                sb.append("  • ").append(sources.get(i).getOrDefault("title", "未知文档")).append("\n");
                            }
                        }
                        String finalAnswer = sb.toString();
                        saveMessage(sessionId, "BOT", finalAnswer);
                        pushToSender(wsSession, Map.of(
                                "type", "message",
                                "sender_type", "BOT",
                                "content", finalAnswer));
                    } else {
                        // RAG 无答案 → 实际转人工，创建 WAITING 会话
                        Map<String, Object> transferResult = agentService.transferToHuman(sessionId);
                        String msg = (String) transferResult.getOrDefault("message", "抱歉，我没有找到相关答案。正在为您转接人工坐席...");
                        pushToSender(wsSession, Map.of(
                                "type", "message",
                                "sender_type", "BOT",
                                "content", msg));
                    }
                }
            } else {
                saveMessage(sessionId, "USER", content);
                sessionManager.broadcast(sessionId, wsSession.getId(), Map.of(
                        "type", "message",
                        "sender_type", "USER",
                        "content", content));
            }
        }
    }

    @Override
    public void handleTransportError(WebSocketSession wsSession, Throwable exception) {
        log.error("WebSocket 传输异常: {}", exception.getMessage());
        closeConnection(wsSession);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession wsSession, CloseStatus closeStatus) {
        closeConnection(wsSession);
    }

    @Override
    public boolean supportsPartialMessages() {
        return false;
    }

    // ==================== 内部方法 ====================

    private void closeConnection(WebSocketSession wsSession) {
        String sessionId = extractSessionId(wsSession);
        sessionManager.remove(sessionId, wsSession);
    }

    private String extractSessionId(WebSocketSession wsSession) {
        URI uri = wsSession.getUri();
        if (uri != null) {
            String path = uri.getPath();
            String[] parts = path.split("/");
            if (parts.length >= 4) return parts[3];
        }
        return "unknown";
    }

    /** 从 WebSocket URL 查询参数中提取值 */
    private String extractQueryParam(WebSocketSession wsSession, String key) {
        URI uri = wsSession.getUri();
        if (uri != null && uri.getQuery() != null) {
            for (String param : uri.getQuery().split("&")) {
                String[] kv = param.split("=", 2);
                if (kv.length == 2 && key.equals(kv[0])) {
                    return kv[1];
                }
            }
        }
        return null;
    }

    /** 从 JSON payload 中提取 content 字段 */
    @SuppressWarnings("unchecked")
    private String extractContent(String payload) {
        try {
            Map<String, Object> map = objectMapper.readValue(payload, Map.class);
            Object v = map.get("content");
            return v != null ? v.toString() : payload;
        } catch (Exception e) {
            return payload;
        }
    }

    /** 从 JSON payload 中提取 sender_type（USER 或 AGENT） */
    @SuppressWarnings("unchecked")
    private String extractSenderType(String payload, String defaultType) {
        try {
            Map<String, Object> map = objectMapper.readValue(payload, Map.class);
            Object v = map.get("sender_type");
            return v != null ? v.toString() : defaultType;
        } catch (Exception e) {
            return defaultType;
        }
    }

    /** 保存消息到 DB（自动脱敏 + 敏感词过滤） */
    private void saveMessage(String sessionId, String senderType, String content) {
        String safeContent = sensitiveWordFilter.sanitize(content);
        CsChatMessage msg = new CsChatMessage();
        msg.setSessionId(sessionId);
        msg.setSenderType(senderType);
        msg.setContent(safeContent);
        msg.setMsgType("TEXT");
        messageMapper.insert(msg);
    }

    /** 从 sessionId 提取 userId：user-4-1719... → "4"，user-2 → "2" */
    private String extractUserId(String sessionId) {
        if (sessionId != null && sessionId.startsWith("user-")) {
            String remainder = sessionId.substring(5);
            int dash = remainder.indexOf('-');
            String uidPart = dash > 0 ? remainder.substring(0, dash) : remainder;
            if (uidPart.matches("\\d+")) return uidPart;
        }
        return "";
    }

    /** 推送消息到发送者本人 */
    private void pushToSender(WebSocketSession wsSession, Object data) {
        try {
            if (wsSession.isOpen()) {
                String json = objectMapper.writeValueAsString(data);
                synchronized (wsSession) {
                    wsSession.sendMessage(new TextMessage(json));
                }
            }
        } catch (Exception e) {
            log.warn("推送消息失败: {}", e.getMessage());
        }
    }
}
