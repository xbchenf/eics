package com.eics.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.eics.entity.CsServiceOrder;
import com.eics.entity.CsSatisfaction;
import com.eics.mapper.CsChatSessionMapper;
import com.eics.mapper.CsServiceOrderMapper;
import com.eics.mapper.EkDocumentMapper;
import com.eics.mapper.CsSatisfactionMapper;
import com.eics.service.DashboardService;
import com.eics.service.AgentStatusManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private final CsChatSessionMapper sessionMapper;
    private final CsServiceOrderMapper orderMapper;
    private final EkDocumentMapper documentMapper;
    private final CsSatisfactionMapper satisfactionMapper;
    private final AgentStatusManager statusManager;

    @Override
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new LinkedHashMap<>();

        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime todayEnd = LocalDate.now().atTime(LocalTime.MAX);

        // 今日会话
        Long todaySessions = sessionMapper.selectCount(new LambdaQueryWrapper<com.eics.entity.CsChatSession>()
                .between(com.eics.entity.CsChatSession::getCreateTime, todayStart, todayEnd));
        stats.put("todaySessions", todaySessions);

        // 今日工单 + 待处理 + 超时
        Long todayOrders = orderMapper.selectCount(new LambdaQueryWrapper<CsServiceOrder>()
                .between(CsServiceOrder::getCreateTime, todayStart, todayEnd));
        Long pendingOrders = orderMapper.selectCount(new LambdaQueryWrapper<CsServiceOrder>()
                .eq(CsServiceOrder::getStatus, "PENDING"));
        Long overdueOrders = orderMapper.selectCount(new LambdaQueryWrapper<CsServiceOrder>()
                .eq(CsServiceOrder::getStatus, "PENDING")
                .lt(CsServiceOrder::getSlaDeadline, LocalDateTime.now()));
        stats.put("todayOrders", todayOrders);
        stats.put("pendingOrders", pendingOrders);
        stats.put("overdueOrders", overdueOrders);

        // 在线坐席
        stats.put("onlineAgents", statusManager.onlineCount());

        // 知识库文档
        Long totalDocs = documentMapper.selectCount(new LambdaQueryWrapper<com.eics.entity.EkDocument>()
                .eq(com.eics.entity.EkDocument::getDeleted, 0));
        stats.put("totalDocuments", totalDocs);

        // 满意度
        List<CsSatisfaction> allSatisfaction = satisfactionMapper.selectList(null);
        double avgRating = allSatisfaction.stream().mapToInt(CsSatisfaction::getRating).average().orElse(0.0);
        stats.put("avgSatisfaction", Math.round(avgRating * 10.0) / 10.0);
        stats.put("satisfactionCount", allSatisfaction.size());

        // 工单优先级分布
        Map<String, Long> priorityDist = new LinkedHashMap<>();
        for (String p : List.of("P0", "P1", "P2", "P3")) {
            Long count = orderMapper.selectCount(new LambdaQueryWrapper<CsServiceOrder>()
                    .eq(CsServiceOrder::getPriority, p)
                    .in(CsServiceOrder::getStatus, "PENDING", "PROCESSING"));
            priorityDist.put(p, count);
        }
        stats.put("priorityDist", priorityDist);

        // 近 7 日趋势
        List<Integer> weeklySessions = new ArrayList<>();
        List<Integer> weeklyOrders = new ArrayList<>();
        List<String> weekLabels = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate day = LocalDate.now().minusDays(i);
            LocalDateTime dayStart = day.atStartOfDay();
            LocalDateTime dayEnd = day.atTime(LocalTime.MAX);
            Long sCount = sessionMapper.selectCount(new LambdaQueryWrapper<com.eics.entity.CsChatSession>()
                    .between(com.eics.entity.CsChatSession::getCreateTime, dayStart, dayEnd));
            Long oCount = orderMapper.selectCount(new LambdaQueryWrapper<CsServiceOrder>()
                    .between(CsServiceOrder::getCreateTime, dayStart, dayEnd));
            weeklySessions.add(sCount.intValue());
            weeklyOrders.add(oCount.intValue());
            weekLabels.add(day.getMonthValue() + "/" + day.getDayOfMonth());
        }
        stats.put("weekLabels", weekLabels);
        stats.put("weeklySessions", weeklySessions);
        stats.put("weeklyOrders", weeklyOrders);

        return stats;
    }
}
