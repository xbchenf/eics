package com.eics.controller;

import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.eics.common.Result;
import com.eics.entity.CsQuickReply;
import com.eics.entity.SysUser;
import com.eics.mapper.SysUserMapper;
import com.eics.mapper.CsQuickReplyMapper;
import com.eics.service.AgentService;
import com.eics.service.AgentStatusManager;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "人工坐席", description = "转接 / 接入 / 发送 / 关闭会话")
@Slf4j
@RestController
@RequestMapping("/api/v1/agent")
@RequiredArgsConstructor
public class AgentController {

    private final AgentService agentService;
    private final AgentStatusManager statusManager;
    private final SysUserMapper userMapper;
    private final CsQuickReplyMapper quickReplyMapper;

    /** 转人工 — Rasa Action 调用（公开） */
    @PostMapping("/transfer")
    public Result<Map<String, Object>> transfer(@RequestBody Map<String, String> request) {
        String sessionId = request.getOrDefault("session_id", "unknown");
        Map<String, Object> result = agentService.transferToHuman(sessionId);
        return Result.ok(result);
    }

    /** 坐席接入会话（需登录） */
    @PostMapping("/accept")
    public Result<Map<String, Object>> accept(@RequestBody Map<String, String> request,
                                               HttpServletRequest servletRequest) {
        String sessionId = request.get("session_id");
        Long agentId = (Long) servletRequest.getAttribute("agentId");
        log.info("坐席 {} 接入会话 {}", agentId, sessionId);
        Map<String, Object> result = agentService.acceptSession(sessionId, agentId);
        return Result.ok(result);
    }

    /** 坐席发送消息（需登录） */
    @PostMapping("/send")
    public Result<Void> sendMessage(@RequestBody Map<String, String> request,
                                     HttpServletRequest servletRequest) {
        Long agentId = (Long) servletRequest.getAttribute("agentId");
        agentService.sendMessage(
                request.get("session_id"),
                agentId,
                request.get("content"));
        return Result.ok(null);
    }

    /** 待接入会话列表（需登录） */
    @GetMapping("/waiting")
    public Result<Map<String, Object>> waiting() {
        return Result.ok(Map.of("sessions", agentService.getWaitingSessions()));
    }

    /** 用户端发送消息 — 转人工后使用（公开） */
    @PostMapping("/user-message")
    public Result<Void> userMessage(@RequestBody Map<String, String> request) {
        agentService.saveUserMessage(
                request.get("session_id"),
                request.get("content"));
        return Result.ok(null);
    }

    /** 获取指定会话的消息（公开 — 用户端轮询用） */
    @GetMapping("/messages/{sessionId}")
    public Result<Map<String, Object>> messages(@PathVariable String sessionId) {
        return Result.ok(Map.of("messages", agentService.getSessionMessages(sessionId)));
    }

    /** 坐席关闭会话（需登录） */
    @PostMapping("/close")
    public Result<Void> close(@RequestBody Map<String, String> request,
                               HttpServletRequest servletRequest) {
        Long agentId = (Long) servletRequest.getAttribute("agentId");
        log.info("坐席 {} 关闭会话 {}", agentId, request.get("session_id"));
        agentService.closeSession(
                request.get("session_id"),
                agentId);
        return Result.ok(null);
    }

    @Operation(summary = "坐席上线", description = "设置当前坐席为在线状态（需登录）")
    @PutMapping("/online")
    public Result<Void> goOnline(HttpServletRequest req) {
        Long agentId = (Long) req.getAttribute("agentId");
        String name = (String) req.getAttribute("agentUsername");
        statusManager.setOnline(agentId, name != null ? name : String.valueOf(agentId));
        return Result.ok(null);
    }

    @Operation(summary = "坐席下线", description = "设置当前坐席为离线状态（需登录）")
    @PutMapping("/offline")
    public Result<Void> goOffline(HttpServletRequest req) {
        Long agentId = (Long) req.getAttribute("agentId");
        statusManager.setOffline(agentId);
        return Result.ok(null);
    }

    @Operation(summary = "在线坐席列表", description = "获取当前在线坐席列表和数量")
    @GetMapping("/online")
    public Result<Map<String, Object>> onlineAgents() {
        return Result.ok(Map.of(
                "count", statusManager.onlineCount(),
                "agents", statusManager.getOnlineAgents()
        ));
    }

    // ==================== 坐席管理（管理员功能） ====================

    @Operation(summary = "用户列表", description = "获取所有用户（需登录，管理员）")
    @GetMapping("/list")
    public Result<Map<String, Object>> listAgents() {
        var users = userMapper.selectList(new LambdaQueryWrapper<SysUser>().orderByDesc(SysUser::getCreateTime));
        users.forEach(u -> u.setPasswordHash(null));
        return Result.ok(Map.of("agents", users));
    }

    @Operation(summary = "创建用户", description = "新增用户账号（需登录，管理员）")
    @PostMapping("/create")
    public Result<Void> createAgent(@RequestBody Map<String, String> req) {
        String username = req.get("username");
        String password = req.get("password");
        String name = req.getOrDefault("name", username);
        String role = req.getOrDefault("role", "AGENT");

        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            return Result.badRequest("用户名和密码不能为空");
        }
        if (userMapper.selectCount(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username)) > 0) {
            return Result.fail(400, "用户名已存在");
        }

        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPasswordHash(BCrypt.hashpw(password, BCrypt.gensalt()));
        user.setName(name);
        user.setRole(role);
        user.setStatus("ACTIVE");
        userMapper.insert(user);
        log.info("新用户创建: username={}, role={}", username, role);
        return Result.ok(null);
    }

    @Operation(summary = "切换用户状态", description = "启用/禁用用户账号（需登录，管理员）")
    @PutMapping("/{id}/status")
    public Result<Void> toggleStatus(@PathVariable Long id) {
        SysUser user = userMapper.selectById(id);
        if (user == null) return Result.notFound("用户不存在");
        user.setStatus("ACTIVE".equals(user.getStatus()) ? "DISABLED" : "ACTIVE");
        userMapper.updateById(user);
        log.info("用户状态切换: id={}, username={}, status={}", id, user.getUsername(), user.getStatus());
        return Result.ok(null);
    }

    @Operation(summary = "修改用户角色", description = "管理员修改用户角色 USER/AGENT/ADMIN")
    @PutMapping("/{id}/role")
    public Result<Void> updateRole(@PathVariable Long id, @RequestBody Map<String, String> req) {
        SysUser user = userMapper.selectById(id);
        if (user == null) return Result.notFound("用户不存在");
        String newRole = req.get("role");
        if (newRole == null || !newRole.matches("USER|AGENT|ADMIN")) {
            return Result.badRequest("无效角色");
        }
        user.setRole(newRole);
        userMapper.updateById(user);
        log.info("用户角色变更: id={}, username={}, role={}", id, user.getUsername(), newRole);
        return Result.ok(null);
    }

    // ==================== 快捷回复 ====================

    @Operation(summary = "快捷回复列表", description = "获取当前坐席的快捷回复（含公用）")
    @GetMapping("/quick-replies")
    public Result<java.util.List<CsQuickReply>> quickReplies(HttpServletRequest req) {
        Long agentId = (Long) req.getAttribute("agentId");
        var list = quickReplyMapper.selectList(new LambdaQueryWrapper<CsQuickReply>()
                .and(w -> w.eq(CsQuickReply::getAgentId, agentId).or().isNull(CsQuickReply::getAgentId))
                .orderByAsc(CsQuickReply::getSortOrder));
        return Result.ok(list);
    }

    @Operation(summary = "新增快捷回复", description = "添加一条快捷回复")
    @PostMapping("/quick-replies")
    public Result<Void> addQuickReply(@RequestBody CsQuickReply qr, HttpServletRequest req) {
        Long agentId = (Long) req.getAttribute("agentId");
        qr.setAgentId(agentId);
        qr.setId(null);
        quickReplyMapper.insert(qr);
        return Result.ok(null);
    }

    @Operation(summary = "删除快捷回复", description = "删除一条快捷回复")
    @DeleteMapping("/quick-replies/{id}")
    public Result<Void> deleteQuickReply(@PathVariable Long id, HttpServletRequest req) {
        Long agentId = (Long) req.getAttribute("agentId");
        CsQuickReply qr = quickReplyMapper.selectById(id);
        if (qr != null && (qr.getAgentId() == null || qr.getAgentId().equals(agentId))) {
            quickReplyMapper.deleteById(id);
        }
        return Result.ok(null);
    }
}
