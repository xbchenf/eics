package com.eics.service;

import java.util.List;
import java.util.Map;

/**
 * 人工坐席服务 — 转接、会话管理
 */
public interface AgentService {

    /** 转人工：将当前会话从机器人切换为待人工接入 */
    Map<String, Object> transferToHuman(String sessionId);

    /** 坐席接入会话 */
    Map<String, Object> acceptSession(String sessionId, Long agentId);

    /** 坐席发送消息给用户 */
    void sendMessage(String sessionId, Long agentId, String content);

    /** 用户发送消息（转人工后） */
    void saveUserMessage(String sessionId, String content);

    /** 获取会话全部历史消息 */
    List<Map<String, Object>> getSessionMessages(String sessionId);

    /** 坐席关闭会话 */
    void closeSession(String sessionId, Long agentId);

    /** 获取待接入会话列表 */
    List<Map<String, Object>> getWaitingSessions();
}
