package com.eics.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.eics.entity.CsServiceOrder;
import com.eics.mapper.CsServiceOrderMapper;
import com.eics.websocket.WebSocketSessionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * SLA 超时监控 — 每分钟检查一次，WebSocket 实时推送到在线坐席
 */
@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
public class SlaMonitor {

    private final CsServiceOrderMapper orderMapper;
    private final WebSocketSessionManager wsManager;

    /** 已推送过的工单 ID，避免重复告警 */
    private final Set<String> notifiedOrderIds = new HashSet<>();

    @Scheduled(fixedRate = 60000) // 每分钟
    public void checkSla() {
        List<CsServiceOrder> overdue = orderMapper.selectList(
                new LambdaQueryWrapper<CsServiceOrder>()
                        .eq(CsServiceOrder::getStatus, "PENDING")
                        .isNotNull(CsServiceOrder::getSlaDeadline)
                        .lt(CsServiceOrder::getSlaDeadline, LocalDateTime.now())
        );

        // 过滤掉已通知过的
        List<CsServiceOrder> newOverdue = overdue.stream()
                .filter(o -> !notifiedOrderIds.contains(o.getId()))
                .collect(Collectors.toList());

        for (CsServiceOrder o : newOverdue) {
            log.warn("[SLA告警] 工单超时: id={}, priority={}, sla={}, createTime={}",
                    o.getId(), o.getPriority(), o.getSlaDeadline(), o.getCreateTime());
            notifiedOrderIds.add(o.getId());
        }

        // 清理已处理的工单（不再 PENDING 的）
        notifiedOrderIds.removeIf(id ->
                orderMapper.selectCount(new LambdaQueryWrapper<CsServiceOrder>()
                        .eq(CsServiceOrder::getId, id)
                        .eq(CsServiceOrder::getStatus, "PENDING")) == 0);

        // WebSocket 推送告警
        if (!newOverdue.isEmpty()) {
            int totalOverdue = overdue.size();
            wsManager.broadcastToAgents(Map.of(
                    "type", "sla_alert",
                    "totalOverdue", totalOverdue,
                    "newCount", newOverdue.size(),
                    "orders", newOverdue.stream().map(o -> Map.of(
                            "id", o.getId().length() > 12 ? o.getId().substring(0, 12) + "..." : o.getId(),
                            "issueType", o.getIssueType() != null ? o.getIssueType() : "",
                            "priority", o.getPriority()
                    )).collect(Collectors.toList())
            ));
        }
    }
}
