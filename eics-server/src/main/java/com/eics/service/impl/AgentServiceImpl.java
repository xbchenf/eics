package com.eics.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.eics.entity.CsChatMessage;
import com.eics.entity.CsChatSession;
import com.eics.entity.CsSatisfaction;
import com.eics.mapper.CsChatMessageMapper;
import com.eics.mapper.CsChatSessionMapper;
import com.eics.mapper.CsSatisfactionMapper;
import com.eics.security.SensitiveWordFilter;
import com.eics.service.AgentService;
import com.eics.websocket.WebSocketSessionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 人工坐席服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentServiceImpl implements AgentService {

    private final CsChatSessionMapper sessionMapper;
    private final CsChatMessageMapper messageMapper;
    private final CsSatisfactionMapper satisfactionMapper;
    private final WebSocketSessionManager wsManager;
    private final SensitiveWordFilter sensitiveWordFilter;

    @Override
    @Transactional
    public Map<String, Object> transferToHuman(String sessionId) {
        CsChatSession session = sessionMapper.selectById(sessionId);

        // 如果会话不存在，新建一个
        if (session == null) {
            session = new CsChatSession();
            session.setId(sessionId);
            session.setUserId(extractUserId(sessionId));
            session.setStatus("WAITING");
            sessionMapper.insert(session);
        } else {
            // 补填 user_id（存量会话可能为空）
            if (session.getUserId() == null || session.getUserId().isEmpty()) {
                session.setUserId(extractUserId(sessionId));
            }
            session.setStatus("WAITING");
            sessionMapper.updateById(session);
        }

        log.info("会话 {} 已转人工，等待坐席接入", sessionId);
        return Map.of("session_id", sessionId, "status", "WAITING",
                "message", "正在为您转接人工坐席，请稍候...");
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

    @Override
    @Transactional
    public Map<String, Object> acceptSession(String sessionId, Long agentId) {
        CsChatSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new RuntimeException("会话不存在: " + sessionId);
        }

        session.setStatus("AGENT");
        session.setAgentId(agentId);
        sessionMapper.updateById(session);

        // 加载历史消息
        List<Map<String, Object>> messages = getSessionMessages(sessionId);

        Map<String, Object> result = new HashMap<>();
        result.put("session_id", sessionId);
        result.put("status", "AGENT");
        result.put("agent_id", agentId);
        result.put("messages", messages);

        log.info("坐席 {} 已接入会话 {}", agentId, sessionId);
        return result;
    }

    @Override
    public void sendMessage(String sessionId, Long agentId, String content) {
        String safeContent = sensitiveWordFilter.sanitize(content);
        CsChatMessage message = new CsChatMessage();
        message.setSessionId(sessionId);
        message.setSenderType("AGENT");
        message.setContent(safeContent);
        message.setMsgType("TEXT");
        messageMapper.insert(message);

        // WebSocket 实时推送给用户
        wsManager.broadcast(sessionId, null, Map.of(
                "type", "message",
                "sender_type", "AGENT",
                "content", content,
                "timestamp", message.getCreateTime() != null ?
                        message.getCreateTime().toString() : ""
        ));
    }

    @Override
    public void saveUserMessage(String sessionId, String content) {
        String safeContent = sensitiveWordFilter.sanitize(content);
        CsChatMessage message = new CsChatMessage();
        message.setSessionId(sessionId);
        message.setSenderType("USER");
        message.setContent(safeContent);
        message.setMsgType("TEXT");
        messageMapper.insert(message);

        // WebSocket 实时推送给坐席
        wsManager.broadcast(sessionId, null, Map.of(
                "type", "message",
                "sender_type", "USER",
                "content", content,
                "timestamp", message.getCreateTime() != null ?
                        message.getCreateTime().toString() : ""
        ));
    }

    @Override
    public List<Map<String, Object>> getSessionMessages(String sessionId) {
        List<CsChatMessage> messages = messageMapper.selectBySessionId(sessionId);

        List<Map<String, Object>> result = new ArrayList<>();
        for (CsChatMessage msg : messages) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", msg.getId().toString());
            item.put("sender_type", msg.getSenderType());
            item.put("content", msg.getContent());
            item.put("msg_type", msg.getMsgType());
            item.put("timestamp", msg.getCreateTime() != null ?
                    msg.getCreateTime().toString() : "");
            result.add(item);
        }
        return result;
    }

    @Override
    @Transactional
    public void closeSession(String sessionId, Long agentId) {
        CsChatSession session = sessionMapper.selectById(sessionId);
        if (session != null) {
            session.setStatus("CLOSED");
            session.setCloseTime(LocalDateTime.now());
            sessionMapper.updateById(session);
            log.info("会话 {} 已被坐席 {} 关闭", sessionId, agentId);
        }
        // WebSocket 推送关闭通知给用户
        wsManager.broadcast(sessionId, null, Map.of(
                "type", "session_closed",
                "content", "人工服务已结束，如需继续咨询请重新提问。",
                "timestamp", LocalDateTime.now().toString()
        ));

        // 推送满意度评价卡片（每会话只有一条评价，唯一索引保证）
        boolean alreadyRated = satisfactionMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CsSatisfaction>()
                        .eq(CsSatisfaction::getSessionId, sessionId)) > 0;
        if (!alreadyRated) {
            wsManager.broadcast(sessionId, null, Map.of(
                    "type", "satisfaction",
                    "sessionId", sessionId,
                    "message", "请为本次服务评分"
            ));
        }

        // 对话状态由 Redis TTL 自动过期，无需手动清理
    }

    @Override
    public List<Map<String, Object>> getWaitingSessions() {
        LambdaQueryWrapper<CsChatSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CsChatSession::getStatus, "WAITING")
                .orderByAsc(CsChatSession::getCreateTime);

        List<CsChatSession> sessions = sessionMapper.selectList(wrapper);
        List<Map<String, Object>> result = new ArrayList<>();
        for (CsChatSession s : sessions) {
            Map<String, Object> item = new HashMap<>();
            item.put("session_id", s.getId());
            item.put("user_id", s.getUserId());
            item.put("create_time", s.getCreateTime() != null ?
                    s.getCreateTime().toString() : "");
            result.add(item);
        }
        return result;
    }
}
