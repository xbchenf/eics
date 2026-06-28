package com.eics.dialog;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;

/**
 * 意图路由 —— 关键词精确匹配优先，LLM 辅助分类兜底
 */
@Slf4j
@Component
public class IntentRouter {

    private static final Map<String, String> KEYWORD_INTENTS = Map.ofEntries(
            Map.entry("转人工", "request_human"),
            Map.entry("人工", "request_human"),
            Map.entry("工单", "ticket_create"),
            Map.entry("报修", "ticket_create"),
            Map.entry("提交工单", "ticket_create"),
            Map.entry("IT工单", "ticket_create"),
            Map.entry("你好", "greet"),
            Map.entry("您好", "greet"),
            Map.entry("在吗", "greet"),
            Map.entry("hello", "greet"),
            Map.entry("hi", "greet")
    );

    private static final Set<String> VALID_INTENTS = Set.of(
            "faq_ask", "ticket_create", "request_human", "greet"
    );

    private final ChatClient chatClient;
    private final int llmTimeout = 2; // 秒

    public IntentRouter(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    /**
     * 根据用户消息解析意图
     */
    public String resolve(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return "faq_ask";
        }

        // 1. 关键词精确匹配（<1ms，零成本，100% 可靠）
        String lower = userMessage.toLowerCase().trim();
        for (var entry : KEYWORD_INTENTS.entrySet()) {
            if (lower.contains(entry.getKey())) {
                return entry.getValue();
            }
        }

        // 2. LLM 辅助分类（仅用于 faq_ask vs greet，不用于 ticket_create/request_human）
        //    因为 LLM 不稳定，误判"我的电脑蓝屏了"为 ticket_create 会跳过 FAQ
        String llmResult = llmClassify(userMessage);
        if (llmResult != null && !"ticket_create".equals(llmResult) && !"request_human".equals(llmResult)) {
            return llmResult;
        }

        // 3. 默认 FAQ
        return "faq_ask";
    }

    /**
     * LLM 辅助意图分类，2s 超时自动降级
     */
    private String llmClassify(String message) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<String> future = executor.submit(() -> {
                String result = chatClient.prompt()
                        .system("""
                                你是一个意图分类器。根据用户消息判断意图，只返回以下之一：
                                faq_ask - 知识库问题（产品说明、公司制度、常见FAQ等）
                                ticket_create - 提交工单（报修、IT支持、权限申请等）
                                request_human - 要求转人工坐席
                                greet - 打招呼/问候

                                只返回意图标识，不要返回其他内容。""")
                        .user(message)
                        .call()
                        .content();
                return result != null ? result.trim().toLowerCase() : null;
            });
            String result = future.get(llmTimeout, TimeUnit.SECONDS);
            if (result != null && VALID_INTENTS.contains(result)) {
                log.debug("意图路由(LLM): {} → {}", message, result);
                return result;
            }
        } catch (TimeoutException e) {
            log.debug("LLM 意图分类超时，降级到 faq_ask");
        } catch (Exception e) {
            log.debug("LLM 意图分类失败: {}", e.getMessage());
        } finally {
            executor.shutdown();
        }
        return null;
    }
}
