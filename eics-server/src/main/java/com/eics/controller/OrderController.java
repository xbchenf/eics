package com.eics.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.eics.common.Result;
import com.eics.entity.CsServiceOrder;
import com.eics.mapper.CsServiceOrderMapper;
import com.eics.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "客服工单", description = "工单创建 / 列表 / 详情")
@Slf4j
@RestController
@RequestMapping("/api/v1/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final CsServiceOrderMapper orderMapper;

    @Operation(summary = "创建工单", description = "Rasa Action 调用，根据表单参数生成工单")
    @PostMapping("/create")
    public Result<Map<String, Object>> create(@RequestBody Map<String, String> params) {
        CsServiceOrder order = orderService.createOrder(params);
        return Result.ok(Map.of("order_id", order.getId(), "status", order.getStatus()));
    }

    @Operation(summary = "工单列表", description = "分页查询工单，支持状态筛选和关键词搜索（需登录）")
    @GetMapping("/list")
    public Result<Map<String, Object>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {

        LambdaQueryWrapper<CsServiceOrder> wrapper = new LambdaQueryWrapper<>();
        if (status != null && !status.isBlank()) {
            wrapper.eq(CsServiceOrder::getStatus, status);
        }
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w
                .like(CsServiceOrder::getPhone, keyword)
                .or().like(CsServiceOrder::getFaultDescription, keyword)
                .or().like(CsServiceOrder::getIssueType, keyword));
        }
        wrapper.orderByDesc(CsServiceOrder::getCreateTime);

        Page<CsServiceOrder> pageResult = orderMapper.selectPage(new Page<>(page, size), wrapper);

        return Result.ok(Map.of(
                "records", pageResult.getRecords(),
                "total", pageResult.getTotal(),
                "page", page,
                "size", size
        ));
    }

    @Operation(summary = "我的工单", description = "当前用户提交的工单列表")
    @GetMapping("/my")
    public Result<Map<String, Object>> myOrders(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            HttpServletRequest req) {
        Long userId = (Long) req.getAttribute("userId");
        String userKey = "user-" + userId;
        LambdaQueryWrapper<CsServiceOrder> wrapper = new LambdaQueryWrapper<CsServiceOrder>()
                .eq(CsServiceOrder::getSessionId, userKey)
                .orderByDesc(CsServiceOrder::getCreateTime);
        // Also find orders from anonymous sessions if user was anonymous before login
        Page<CsServiceOrder> pageResult = orderMapper.selectPage(new Page<>(page, size), wrapper);
        return Result.ok(Map.of("records", pageResult.getRecords(),
                "total", pageResult.getTotal(), "page", page, "size", size));
    }

    @Operation(summary = "工单详情", description = "根据工单ID查询详情（需登录）")
    @GetMapping("/{orderId}")
    public Result<CsServiceOrder> detail(@PathVariable String orderId) {
        return Result.ok(orderService.getById(orderId));
    }

    @Operation(summary = "认领工单", description = "坐席认领工单，状态变更为 PROCESSING（需登录）")
    @PutMapping("/{orderId}/assign")
    public Result<Void> assign(@PathVariable String orderId, HttpServletRequest req) {
        Long agentId = (Long) req.getAttribute("agentId");
        orderService.assign(orderId, agentId);
        log.info("坐席 {} 认领工单 {}", agentId, orderId);
        return Result.ok(null);
    }

    @Operation(summary = "更新优先级", description = "坐席手动调整工单优先级（需登录）")
    @PutMapping("/{orderId}/priority")
    public Result<Void> updatePriority(@PathVariable String orderId, @RequestBody Map<String, String> req) {
        CsServiceOrder order = orderMapper.selectById(orderId);
        if (order != null) {
            order.setPriority(req.get("priority"));
            orderMapper.updateById(order);
        }
        return Result.ok(null);
    }

    @Operation(summary = "解决工单", description = "标记工单为已解决，状态变更为 RESOLVED（需登录）")
    @PutMapping("/{orderId}/resolve")
    public Result<Void> resolve(@PathVariable String orderId) {
        orderService.resolve(orderId);
        log.info("工单 {} 已解决", orderId);
        return Result.ok(null);
    }
}
