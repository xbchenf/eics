package com.eics.controller;

import com.eics.common.Result;
import com.eics.dialog.DialogService;
import com.eics.mapper.CsServiceOrderMapper;
import com.eics.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 对话接口 — V2.0 新核心（替代 Rasa webhook）
 *
 * POST /api/v1/dialog/message   接收用户消息，返回回复
 * DELETE /api/v1/dialog/context/{sessionId}  重置对话状态
 */
@Tag(name = "对话引擎", description = "替代 Rasa，Java 原生对话管理")
@Slf4j
@RestController
@RequestMapping("/api/v1/dialog")
@RequiredArgsConstructor
public class DialogController {

    private final DialogService dialogService;
    private final OrderService orderService;

    @Operation(summary = "发送消息", description = "接收用户消息，返回回复列表")
    @PostMapping("/message")
    public Result<List<Map<String, Object>>> message(@RequestBody Map<String, String> request) {
        String senderId = request.getOrDefault("sender", request.get("session_id"));
        String message = request.get("message");
        if (message == null) message = request.get("content");

        log.debug("Dialog 消息: sender={}, message={}", senderId, message);
        List<Map<String, Object>> replies = dialogService.processMessage(senderId, message);
        return Result.ok(replies);
    }

    @Operation(summary = "重置对话", description = "清除指定会话的对话状态和槽位")
    @DeleteMapping("/context/{sessionId}")
    public Result<Void> resetContext(@PathVariable String sessionId) {
        dialogService.resetContext(sessionId);
        return Result.ok(null);
    }
}
