package com.eics.controller;

import com.eics.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

@Tag(name = "健康检查", description = "连通性测试 & 中间件心跳探测")
@RestController
@RequestMapping("/api/v1")
public class HealthController {

    @Operation(summary = "健康检查", description = "返回服务运行状态，Docker / K8s 探活用")
    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        return Result.ok(Map.of(
                "status", "UP",
                "service", "EICS-Server",
                "timestamp", LocalDateTime.now()
        ));
    }
}
