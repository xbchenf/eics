package com.eics.controller;

import com.eics.common.Result;
import com.eics.service.SatisfactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "满意度评价", description = "用户提交评价 / 查询统计")
@Slf4j
@RestController
@RequestMapping("/api/v1/satisfaction")
@RequiredArgsConstructor
public class SatisfactionController {

    private final SatisfactionService satisfactionService;

    @Operation(summary = "提交评价", description = "用户对已关闭的会话进行 1-5 星评分")
    @PostMapping
    public Result<Map<String, Object>> submit(@RequestBody Map<String, Object> request,
                                               HttpServletRequest servletRequest) {
        Long userId = (Long) servletRequest.getAttribute("userId");
        if (userId == null) {
            return Result.fail(401, "请先登录");
        }

        String sessionId = (String) request.get("sessionId");
        Integer rating = request.get("rating") instanceof Integer ?
                (Integer) request.get("rating") : Integer.valueOf(request.get("rating").toString());
        String comment = (String) request.get("comment");

        if (sessionId == null || sessionId.isBlank()) {
            return Result.badRequest("会话ID不能为空");
        }
        if (rating == null || rating < 1 || rating > 5) {
            return Result.badRequest("评分必须在 1-5 之间");
        }

        try {
            Map<String, Object> result = satisfactionService.submit(sessionId, userId, rating, comment);
            return Result.ok(result);
        } catch (IllegalArgumentException e) {
            return Result.badRequest(e.getMessage());
        }
    }

    @Operation(summary = "满意度统计", description = "管理员看全局 / 坐席看含个人数据")
    @GetMapping("/stats")
    public Result<Map<String, Object>> stats(HttpServletRequest servletRequest) {
        Long agentId = (Long) servletRequest.getAttribute("agentId");
        return Result.ok(satisfactionService.stats(agentId));
    }

    @Operation(summary = "待评价列表", description = "当前用户已关闭但未评价的会话列表")
    @GetMapping("/pending")
    public Result<List<Map<String, Object>>> pending(HttpServletRequest servletRequest) {
        Long userId = (Long) servletRequest.getAttribute("userId");
        if (userId == null) {
            return Result.fail(401, "请先登录");
        }
        return Result.ok(satisfactionService.pending(userId));
    }

    @Operation(summary = "我的评价记录", description = "坐席查看自己被评价的记录")
    @GetMapping("/my")
    public Result<Map<String, Object>> myRatings(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest servletRequest) {
        Long agentId = (Long) servletRequest.getAttribute("agentId");
        if (agentId == null) {
            return Result.fail(401, "请先登录");
        }
        List<Map<String, Object>> list = satisfactionService.myRatings(agentId, page, size);
        return Result.ok(Map.of("records", list, "total", list.size()));
    }
}
