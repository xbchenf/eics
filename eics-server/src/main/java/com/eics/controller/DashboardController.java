package com.eics.controller;

import com.eics.common.Result;
import com.eics.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "工作台", description = "Dashboard 聚合统计数据")
@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @Operation(summary = "工作台统计", description = "今日会话/工单/优先级分布/7日趋势等聚合数据")
    @GetMapping
    public Result<Map<String, Object>> stats() {
        return Result.ok(dashboardService.getStats());
    }
}
