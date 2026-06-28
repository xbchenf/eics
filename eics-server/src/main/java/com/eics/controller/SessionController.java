package com.eics.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.eics.common.Result;
import com.eics.entity.CsChatSession;
import com.eics.entity.SysUser;
import com.eics.mapper.CsChatSessionMapper;
import com.eics.mapper.SysUserMapper;
import com.eics.service.AgentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@Tag(name = "会话管理", description = "会话列表 / 详情 / 消息查询")
/**
 * 会话管理接口 — /api/v1/session/**
 *
 * 权限说明：
 * - /list      需要坐席登录（AuthInterceptor 保护）
 * - /{id}      公开（用户可通过 WebSocket 获取到 sessionId 后查看）
 * - /{id}/messages  公开（等同 /api/v1/agent/messages/{id}）
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/session")
@RequiredArgsConstructor
public class SessionController {

    private final CsChatSessionMapper sessionMapper;
    private final SysUserMapper userMapper;
    private final AgentService agentService;

    /**
     * 会话列表（分页 + 可选状态筛选）— 坐席端使用
     */
    @GetMapping("/list")
    public Result<Map<String, Object>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {

        LambdaQueryWrapper<CsChatSession> wrapper = new LambdaQueryWrapper<>();
        if (status != null && !status.isBlank()) {
            wrapper.eq(CsChatSession::getStatus, status);
        }
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w
                .like(CsChatSession::getId, keyword)
                .or().like(CsChatSession::getUserId, keyword));
        }
        wrapper.orderByDesc(CsChatSession::getCreateTime);

        Page<CsChatSession> pageResult = sessionMapper.selectPage(new Page<>(page, size), wrapper);

        // 批量查询 userId → userName
        Set<String> userIds = pageResult.getRecords().stream()
                .map(CsChatSession::getUserId)
                .filter(uid -> uid != null && !uid.isEmpty() && uid.matches("\\d+"))
                .collect(Collectors.toSet());
        Map<String, String> nameMap = Collections.emptyMap();
        if (!userIds.isEmpty()) {
            List<SysUser> users = userMapper.selectBatchIds(
                    userIds.stream().map(Long::valueOf).collect(Collectors.toList()));
            nameMap = users.stream().collect(Collectors.toMap(
                    u -> u.getId().toString(), u -> u.getName() != null ? u.getName() : u.getUsername()));
        }
        final Map<String, String> finalNameMap = nameMap;

        List<Map<String, Object>> records = pageResult.getRecords().stream().map(s -> {
            Map<String, Object> item = new HashMap<>();
            item.put("id", s.getId());
            item.put("userId", s.getUserId());
            item.put("userName", finalNameMap.getOrDefault(s.getUserId(), s.getUserId()));
            item.put("status", s.getStatus());
            item.put("agentId", s.getAgentId());
            item.put("createTime", s.getCreateTime() != null ? s.getCreateTime().toString() : "");
            item.put("updateTime", s.getUpdateTime() != null ? s.getUpdateTime().toString() : "");
            item.put("closeTime", s.getCloseTime() != null ? s.getCloseTime().toString() : "");
            return item;
        }).collect(Collectors.toList());

        return Result.ok(Map.of(
                "records", records,
                "total", pageResult.getTotal(),
                "page", page,
                "size", size
        ));
    }

    /**
     * 会话详情
     */
    @GetMapping("/{id}")
    public Result<CsChatSession> detail(@PathVariable String id) {
        CsChatSession session = sessionMapper.selectById(id);
        if (session == null) {
            return Result.notFound("会话不存在");
        }
        return Result.ok(session);
    }

    /**
     * 会话消息列表（按时间正序）
     */
    @GetMapping("/{id}/messages")
    public Result<Map<String, Object>> messages(@PathVariable String id) {
        CsChatSession session = sessionMapper.selectById(id);
        if (session == null) {
            return Result.notFound("会话不存在");
        }
        return Result.ok(Map.of("messages", agentService.getSessionMessages(id)));
    }

    /**
     * 按用户ID查询会话
     */
    @GetMapping("/by-user/{userId}")
    public Result<Map<String, Object>> byUser(@PathVariable String userId) {
        var list = sessionMapper.selectList(
                new LambdaQueryWrapper<CsChatSession>()
                        .eq(CsChatSession::getUserId, userId)
                        .orderByDesc(CsChatSession::getCreateTime)
        );
        return Result.ok(Map.of("sessions", list));
    }
}
