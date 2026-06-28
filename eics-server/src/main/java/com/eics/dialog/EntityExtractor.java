package com.eics.dialog;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.*;
import java.util.regex.Pattern;

/**
 * 实体提取器 —— 正则优先 + LLM 增强
 */
@Slf4j
@Component
public class EntityExtractor {

    private static final Pattern PHONE_PATTERN = Pattern.compile("1[3-9]\\d{9}");
    private static final Set<String> ISSUE_TYPES = Set.of(
            "硬件故障", "软件问题", "网络问题", "权限申请", "其他"
    );

    private final int llmTimeout;
    private final ChatClient chatClient;

    public EntityExtractor(@Value("${dialog.llm-extract-timeout:2}") int llmTimeout,
                           ChatClient.Builder chatClientBuilder) {
        this.llmTimeout = llmTimeout;
        this.chatClient = chatClientBuilder.build();
    }

    /**
     * 从用户消息中提取指定类型的实体
     */
    public String extract(String text, String slotName) {
        if (text == null || text.isBlank()) return null;
        String trimmed = text.trim();

        return switch (slotName) {
            case "phone" -> extractPhone(trimmed);
            case "issue_type" -> extractIssueType(trimmed);
            case "fault_description" -> trimmed;
            default -> null;
        };
    }

    // ==================== 手机号提取 ====================

    private String extractPhone(String text) {
        var matcher = PHONE_PATTERN.matcher(text);
        if (matcher.find()) {
            log.debug("手机号提取(正则): {}", matcher.group());
            return matcher.group();
        }
        String digits = text.replaceAll("\\s+", "");
        if (digits.matches("\\d{11}") && digits.startsWith("1")) {
            log.debug("手机号提取(纯数字): {}", digits);
            return digits;
        }
        // LLM 增强：从自然语言中提取
        return llmExtract(text, "11位中国大陆手机号", "只返回纯数字，提取不到返回 NONE");
    }

    // ==================== 问题类型提取 ====================

    private String extractIssueType(String text) {
        if (ISSUE_TYPES.contains(text)) return text;
        String lower = text.toLowerCase();
        if (lower.contains("硬件") || lower.contains("故障")) return "硬件故障";
        if (lower.contains("软件") || lower.contains("程序")) return "软件问题";
        if (lower.contains("网络") || lower.contains("上网") || lower.contains("wifi")) return "网络问题";
        if (lower.contains("权限") || lower.contains("申请")) return "权限申请";
        // LLM 增强
        String result = llmExtract(text, "问题类型（硬件故障/软件问题/网络问题/权限申请/其他）",
                "只返回这5个中的一个，无法判断返回 NONE");
        return ISSUE_TYPES.contains(result) ? result : null;
    }

    // ==================== LLM 增强 ====================

    /**
     * 调用 LLM 从自然语言中提取实体，带超时降级
     */
    private String llmExtract(String text, String extractWhat, String instruction) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<String> future = executor.submit(() -> {
                String result = chatClient.prompt()
                        .user("从以下文本中提取" + extractWhat + "。\n" + instruction + "\n\n文本：" + text)
                        .call()
                        .content();
                return result != null ? result.trim() : null;
            });
            String result = future.get(llmTimeout, TimeUnit.SECONDS);
            if (result != null && !result.isBlank() && !"NONE".equalsIgnoreCase(result)) {
                log.debug("LLM 实体提取: text={}, slot={}, result={}", text, extractWhat, result);
                return result;
            }
        } catch (TimeoutException e) {
            log.debug("LLM 实体提取超时(slot={})，降级到 null", extractWhat);
        } catch (Exception e) {
            log.debug("LLM 实体提取失败(slot={}): {}", extractWhat, e.getMessage());
        } finally {
            executor.shutdown();
        }
        return null;
    }
}
