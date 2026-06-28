package com.eics.dialog;

import com.eics.service.AgentService;
import com.eics.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 对话引擎 —— V2.0 核心，替代 Rasa
 *
 * 编排 IntentRouter + FormStateMachine + EntityExtractor
 * 替代 V1.0 的 RasaClient.sendMessage() 调用
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DialogService {

    private final IntentRouter intentRouter;
    private final FormStateMachine stateMachine;
    private final EntityExtractor entityExtractor;
    private final AgentService agentService;
    private final OrderService orderService;

    /**
     * 处理用户消息，返回回复列表（格式兼容 V1.0 Rasa webhook）
     *
     * @param senderId  会话 ID
     * @param message   用户消息文本
     * @return 回复列表，每条包含 text 和可选的 buttons
     */
    public List<Map<String, Object>> processMessage(String senderId, String message) {
        List<Map<String, Object>> replies = new ArrayList<>();
        FormStateMachine.State state = stateMachine.getState(senderId);

        // 步骤 1：意图路由
        String intent = intentRouter.resolve(message);
        log.debug("会话 {} 意图={}, 状态={}, 消息={}", senderId, intent, state, message);

        // 步骤 2：根据意图处理
        if ("request_human".equals(intent)) {
            stateMachine.reset(senderId);
            // 调用 AgentService 创建 WAITING 会话
            Map<String, Object> transferResult = agentService.transferToHuman(senderId);
            String transferMsg = (String) transferResult.getOrDefault("message", "正在为您转接人工坐席，请稍候...");
            replies.add(Map.of("text", transferMsg));
            return replies;

        } else if ("greet".equals(intent)) {
            if (state == FormStateMachine.State.IDLE) {
                replies.add(Map.of("text", "您好！我是 EICS 智能助手，可以帮您解答产品问题、提交报修工单。请问有什么可以帮您？"));
                return replies;
            }
        } else if ("ticket_create".equals(intent)) {
            if (state == FormStateMachine.State.IDLE) {
                String reply = stateMachine.startTicketForm(senderId);
                replies.add(Map.of("text", reply));
                return replies;
            }
        }

        // 步骤 3：表单流程中 → 提取实体 → 推进状态
        if (state != FormStateMachine.State.IDLE && state != FormStateMachine.State.DONE) {
            String extracted = entityExtractor.extract(message, state.slotName());
            FormStateMachine.ProcessResult result = stateMachine.process(senderId, message, extracted);

            if (result.reply() != null) {
                Map<String, Object> replyMap = new java.util.HashMap<>();
                replyMap.put("text", result.reply());
                // 传递按钮（issue_type 选择）
                if (result.extra().containsKey("buttons")) {
                    replyMap.put("buttons", result.extra().get("buttons"));
                }
                replies.add(replyMap);
            }

            // 表单完成 → 直接创建工单
            if (result.completed()) {
                var order = orderService.createOrder(Map.of(
                        "session_id", senderId,
                        "phone", result.slots().getOrDefault("phone", ""),
                        "issue_type", result.slots().getOrDefault("issue_type", ""),
                        "fault_description", result.slots().getOrDefault("fault_description", ""),
                        "device_id", result.slots().getOrDefault("device_id", "")
                ));
                stateMachine.reset(senderId);
                replies.add(Map.of("text", "您的工单已提交成功！\n工单编号：" + order.getId()
                        + "\n请保持手机畅通，我们的工作人员会尽快联系您。"));
            }
            return replies;
        }

        // 步骤 4：默认 → FAQ 问答（由 RAGService 处理）
        // 返回空列表 → 上层 ChatWebSocketHandler 调 RAGService
        return replies;
    }

    /**
     * 通知引擎会话已关闭，重置对话上下文
     */
    public void resetContext(String senderId) {
        stateMachine.reset(senderId);
    }
}
