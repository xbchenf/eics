package com.eics.dialog;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 多轮表单状态机 —— 替代 Rasa FormAction
 *
 * 状态流转：
 * IDLE → COLLECTING_PHONE → COLLECTING_ISSUE_TYPE → COLLECTING_DESC → DONE → IDLE
 *
 * 状态和槽位存储在 Redis，服务器重启不丢对话上下文。
 */
@Slf4j
@Component
public class FormStateMachine {

    private static final String STATE_KEY = "dialog:%s:state";
    private static final String SLOTS_KEY = "dialog:%s:slots";

    private final StringRedisTemplate redis;
    private final int stateTtlMinutes;

    public FormStateMachine(StringRedisTemplate redis,
                            @Value("${dialog.state-ttl-minutes:30}") int stateTtlMinutes) {
        this.redis = redis;
        this.stateTtlMinutes = stateTtlMinutes;
    }

    /** 状态枚举 */
    public enum State {
        IDLE(""),
        COLLECTING_PHONE("phone"),
        COLLECTING_ISSUE_TYPE("issue_type"),
        COLLECTING_DESC("fault_description"),
        DONE("");

        private final String slotName;

        State(String slotName) { this.slotName = slotName; }

        public String slotName() { return slotName; }
    }

    // ==================== 状态读写 ====================

    public State getState(String sessionId) {
        String raw = redis.opsForValue().get(STATE_KEY.formatted(sessionId));
        if (raw == null) return State.IDLE;
        try { return State.valueOf(raw); } catch (IllegalArgumentException e) { return State.IDLE; }
    }

    public void setState(String sessionId, State state) {
        redis.opsForValue().set(STATE_KEY.formatted(sessionId), state.name(),
                stateTtlMinutes, TimeUnit.MINUTES);
    }

    // ==================== 槽位读写 ====================

    public Map<String, String> getSlots(String sessionId) {
        Map<Object, Object> raw = redis.opsForHash().entries(SLOTS_KEY.formatted(sessionId));
        Map<String, String> slots = new HashMap<>();
        raw.forEach((k, v) -> slots.put(k.toString(), v != null ? v.toString() : ""));
        return slots;
    }

    public void setSlot(String sessionId, String slotName, String value) {
        redis.opsForHash().put(SLOTS_KEY.formatted(sessionId), slotName, value);
        redis.expire(SLOTS_KEY.formatted(sessionId), stateTtlMinutes, TimeUnit.MINUTES);
    }

    // ==================== 核心流程 ====================

    /**
     * 处理用户消息，返回系统回复文本
     *
     * @return Pair<回复文本, 是否已完成表单>
     */
    public ProcessResult process(String sessionId, String userMessage, String extractedValue) {
        State state = getState(sessionId);

        switch (state) {
            case COLLECTING_PHONE:
                return handlePhoneSlot(sessionId, extractedValue);
            case COLLECTING_ISSUE_TYPE:
                return handleIssueTypeSlot(sessionId, extractedValue);
            case COLLECTING_DESC:
                return handleDescSlot(sessionId, userMessage);
            case DONE:
                return new ProcessResult(null, getSlots(sessionId), true);
            default:
                // IDLE → 不做任何处理，由 DialogService 根据意图启动表单
                return new ProcessResult(null, getSlots(sessionId), false);
        }
    }

    /** 启动工单表单 */
    public String startTicketForm(String sessionId) {
        setState(sessionId, State.COLLECTING_PHONE);
        return "请输入您的手机号，方便我们联系您：";
    }

    /** 重置对话上下文 */
    public void reset(String sessionId) {
        redis.delete(STATE_KEY.formatted(sessionId));
        redis.delete(SLOTS_KEY.formatted(sessionId));
        log.debug("对话上下文已重置: session={}", sessionId);
    }

    // ==================== 各槽位处理 ====================

    private ProcessResult handlePhoneSlot(String sessionId, String extractedValue) {
        if (extractedValue != null) {
            setSlot(sessionId, "phone", extractedValue);
            setState(sessionId, State.COLLECTING_ISSUE_TYPE);
            var buttons = List.<Map<String, String>>of(
                    Map.of("title", "硬件故障", "payload", "硬件故障"),
                    Map.of("title", "软件问题", "payload", "软件问题"),
                    Map.of("title", "网络问题", "payload", "网络问题"),
                    Map.of("title", "权限申请", "payload", "权限申请"),
                    Map.of("title", "其他", "payload", "其他"));
            Map<String, Object> extra = new HashMap<>();
            extra.put("buttons", buttons);
            return new ProcessResult("请选择问题类型：", extra, getSlots(sessionId), false);
        }
        return new ProcessResult("手机号格式不正确，请输入11位中国大陆手机号（如 13812345678）：",
                getSlots(sessionId), false);
    }

    private ProcessResult handleIssueTypeSlot(String sessionId, String extractedValue) {
        if (extractedValue != null) {
            setSlot(sessionId, "issue_type", extractedValue);
            setState(sessionId, State.COLLECTING_DESC);
            return new ProcessResult("请描述一下您遇到的具体故障现象：",
                    getSlots(sessionId), false);
        }
        return new ProcessResult("请选择问题类型："
                + "硬件故障 / 软件问题 / 网络问题 / 权限申请 / 其他",
                getSlots(sessionId), false);
    }

    private ProcessResult handleDescSlot(String sessionId, String userMessage) {
        setSlot(sessionId, "fault_description", userMessage);
        setState(sessionId, State.DONE);
        return new ProcessResult(null, getSlots(sessionId), true);
    }

    // ==================== 结果封装 ====================

    public static class ProcessResult {
        private final String reply;
        private final Map<String, String> slots;
        private final boolean completed;
        private final Map<String, Object> extra;

        public ProcessResult(String reply, Map<String, String> slots, boolean completed) {
            this.reply = reply;
            this.slots = new HashMap<>(slots);
            this.completed = completed;
            this.extra = new HashMap<>();
        }

        public ProcessResult(String reply, Map<String, Object> extra, Map<String, String> slots, boolean completed) {
            this.reply = reply;
            this.slots = new HashMap<>(slots);
            this.completed = completed;
            this.extra = new HashMap<>(extra);
        }

        public String reply() { return reply; }
        public Map<String, String> slots() { return slots; }
        public boolean completed() { return completed; }
        public Map<String, Object> extra() { return extra; }
    }
}
