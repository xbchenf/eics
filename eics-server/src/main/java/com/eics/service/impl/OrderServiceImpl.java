package com.eics.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.eics.entity.CsServiceOrder;
import com.eics.mapper.CsServiceOrderMapper;
import com.eics.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final CsServiceOrderMapper orderMapper;

    @Override
    @Transactional
    public CsServiceOrder createOrder(Map<String, String> params) {
        CsServiceOrder order = new CsServiceOrder();
        order.setSessionId(params.get("session_id"));
        order.setPhone(params.get("phone"));
        order.setFaultDescription(params.get("fault_description"));
        order.setDeviceId(params.getOrDefault("device_id", ""));
        order.setIssueType(params.getOrDefault("issue_type", "OTHER"));
        order.setStatus("PENDING");
        // 自动定级：根据问题类型
        order.setPriority(autoPriority(order.getIssueType()));
        order.setSlaDeadline(calcSla(order.getPriority()));
        orderMapper.insert(order);

        log.info("工单创建成功: orderId={}, phone={}", order.getId(),
                order.getPhone().replaceAll("(\\d{3})\\d{4}(\\d{4})", "$1****$2"));
        return order;
    }

    @Override
    public CsServiceOrder getById(String orderId) {
        return orderMapper.selectById(orderId);
    }

    @Override
    @Transactional
    public void assign(String orderId, Long agentId) {
        CsServiceOrder order = orderMapper.selectById(orderId);
        if (order != null) {
            order.setAgentId(agentId);
            order.setStatus("PROCESSING");
            order.setFirstResponseTime(LocalDateTime.now());
            orderMapper.updateById(order);
            log.info("工单 {} 已分配给坐席 {}", orderId, agentId);
        }
    }

    @Override
    @Transactional
    public void resolve(String orderId) {
        CsServiceOrder order = orderMapper.selectById(orderId);
        if (order != null) {
            order.setStatus("RESOLVED");
            order.setResolveTime(LocalDateTime.now());
            orderMapper.updateById(order);
            log.info("工单 {} 已解决", orderId);
        }
    }

    /** 根据问题类型自动定级 */
    private String autoPriority(String issueType) {
        if (issueType == null) return "P2";
        return switch (issueType) {
            case "硬件故障", "网络问题" -> "P1";   // 影响办公
            case "权限申请" -> "P3";                // 不紧急
            default -> "P2";                        // 默认中等
        };
    }

    /** 根据优先级计算 SLA 截止时间 */
    private LocalDateTime calcSla(String priority) {
        return switch (priority) {
            case "P0" -> LocalDateTime.now().plusMinutes(30);
            case "P1" -> LocalDateTime.now().plusHours(2);
            default -> LocalDateTime.now().plusHours(8);  // P2/P3
        };
    }
}
