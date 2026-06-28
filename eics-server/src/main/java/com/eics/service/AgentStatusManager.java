package com.eics.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 坐席在线状态管理 — 内存存储
 * 分布式部署时需改为 Redis
 */
@Slf4j
@Component
public class AgentStatusManager {

    /** 在线坐席 ID 集合 */
    private final Set<Long> onlineAgents = ConcurrentHashMap.newKeySet();

    /** 坐席 ID → 姓名映射（用于显示） */
    private final Map<Long, String> agentNames = new ConcurrentHashMap<>();

    public void setOnline(Long agentId, String name) {
        onlineAgents.add(agentId);
        agentNames.put(agentId, name);
        log.info("坐席上线: id={}, name={}", agentId, name);
    }

    public void setOffline(Long agentId) {
        onlineAgents.remove(agentId);
        log.info("坐席下线: id={}", agentId);
    }

    public boolean isOnline(Long agentId) {
        return onlineAgents.contains(agentId);
    }

    public int onlineCount() {
        return onlineAgents.size();
    }

    public Set<Map.Entry<Long, String>> getOnlineAgents() {
        return onlineAgents.stream()
                .collect(Collectors.toMap(id -> id, id -> agentNames.getOrDefault(id, "未知")))
                .entrySet();
    }
}
