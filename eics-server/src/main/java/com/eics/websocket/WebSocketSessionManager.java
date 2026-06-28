package com.eics.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket 连接管理器 — 按会话 ID 维护连接池
 * 一个 chat session 下可挂多个 WebSocket 连接（用户/坐席各一端）
 */
@Slf4j
@Component
public class WebSocketSessionManager {

    // sessionId → (wsSessionId → WebSocketSession)
    private final Map<String, Map<String, WebSocketSession>> rooms = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 注册连接 */
    public void register(String sessionId, WebSocketSession wsSession) {
        rooms.computeIfAbsent(sessionId, k -> new ConcurrentHashMap<>())
             .put(wsSession.getId(), wsSession);
        log.info("WebSocket 连接加入: session={}, wsId={}, 当前房间连接数={}",
                sessionId, wsSession.getId(), rooms.get(sessionId).size());
    }

    /** 移除连接 */
    public void remove(String sessionId, WebSocketSession wsSession) {
        Map<String, WebSocketSession> conns = rooms.get(sessionId);
        if (conns != null) {
            conns.remove(wsSession.getId());
            if (conns.isEmpty()) rooms.remove(sessionId);
        }
        log.info("WebSocket 连接断开: session={}, wsId={}", sessionId, wsSession.getId());
    }

    /** 向所有已验证坐席连接推送消息（用于全局通知：SLA告警等） */
    public void broadcastToAgents(Object message) {
        try {
            String json = objectMapper.writeValueAsString(message);
            TextMessage textMessage = new TextMessage(json);
            for (var entry : rooms.entrySet()) {
                for (var conn : entry.getValue().entrySet()) {
                    var ws = conn.getValue();
                    if (Boolean.TRUE.equals(ws.getAttributes().get("isAgent")) && ws.isOpen()) {
                        try {
                            synchronized (ws) { ws.sendMessage(textMessage); }
                        } catch (IOException e) { /* skip dead connection */ }
                    }
                }
            }
        } catch (Exception e) {
            log.error("广播SLA告警失败", e);
        }
    }

    /** 向指定会话的所有连接推送消息（排除发送者本人） */
    public void broadcast(String sessionId, String excludeWsId, Object message) {
        Map<String, WebSocketSession> conns = rooms.get(sessionId);
        if (conns == null) return;

        try {
            String json = objectMapper.writeValueAsString(message);
            TextMessage textMessage = new TextMessage(json);

            for (var entry : conns.entrySet()) {
                if (entry.getKey().equals(excludeWsId)) continue;
                try {
                    if (entry.getValue().isOpen()) {
                        synchronized (entry.getValue()) {
                            entry.getValue().sendMessage(textMessage);
                        }
                    }
                } catch (IOException e) {
                    log.warn("推送消息失败: wsId={}", entry.getKey(), e);
                }
            }
        } catch (Exception e) {
            log.error("序列化消息失败", e);
        }
    }
}
