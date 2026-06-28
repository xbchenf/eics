package com.eics.security;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;

/**
 * 敏感词过滤器 — DFA 算法高效匹配
 *
 * 支持：
 * - 敏感词检测（contains / findAll）
 * - 敏感词替换（replace → ***）
 * - 可通过 application.yml 配置开关和自定义词库
 */
@Slf4j
@Component
public class SensitiveWordFilter {

    /** 敏感词 DFA 树根节点 */
    private final Map<Character, Object> dfaRoot = new HashMap<>();

    /** 替换符 */
    private static final String REPLACE_CHAR = "*";

    @Value("${security.sensitive-word.enabled:true}")
    private boolean enabled;

    @Value("${security.sensitive-word.custom-words:}")
    private String customWords;

    /** 内建基础敏感词 */
    private static final Set<String> BUILTIN_WORDS = Set.of(
            "fuck", "shit", "damn",
            "傻逼", "蠢货", "废物", "白痴",
            "草泥马", "他妈", "你妈",
            "强奸", "杀人", "自杀",
            "赌博", "毒品", "枪支",
            "法轮功", "六四", "天安门"
    );

    @PostConstruct
    public void init() {
        Set<String> allWords = new HashSet<>(BUILTIN_WORDS);

        // 加载自定义敏感词（逗号分隔）
        if (customWords != null && !customWords.isBlank()) {
            Arrays.stream(customWords.split(","))
                    .map(String::trim)
                    .filter(w -> !w.isEmpty())
                    .forEach(allWords::add);
        }

        // 构建 DFA 树
        for (String word : allWords) {
            Map<Character, Object> node = dfaRoot;
            for (char c : word.toCharArray()) {
                @SuppressWarnings("unchecked")
                Map<Character, Object> child = (Map<Character, Object>) node.computeIfAbsent(c, k -> new HashMap<>());
                node = child;
            }
            node.put('$', true);  // 终结标记
        }
        log.info("敏感词过滤器已初始化: {} 个词, enabled={}", allWords.size(), enabled);
    }

    /**
     * 检测文本是否包含敏感词
     */
    public boolean contains(String text) {
        if (!enabled || text == null || text.isEmpty()) return false;
        return !findAll(text).isEmpty();
    }

    /**
     * 查找文本中所有敏感词
     */
    public List<String> findAll(String text) {
        List<String> result = new ArrayList<>();
        if (!enabled || text == null || text.isEmpty()) return result;

        int len = text.length();
        for (int i = 0; i < len; i++) {
            Map<Character, Object> node = dfaRoot;
            for (int j = i; j < len; j++) {
                @SuppressWarnings("unchecked")
                Map<Character, Object> child = (Map<Character, Object>) node.get(text.charAt(j));
                if (child == null) break;
                if (child.containsKey('$')) {
                    result.add(text.substring(i, j + 1));
                }
                node = child;
            }
        }
        return result;
    }

    /**
     * 将敏感词替换为 ***
     */
    public String replace(String text) {
        if (!enabled || text == null || text.isEmpty()) return text;

        List<String> words = findAll(text);
        StringBuilder sb = new StringBuilder(text);
        for (String word : words) {
            int idx = sb.indexOf(word);
            while (idx >= 0) {
                sb.replace(idx, idx + word.length(), REPLACE_CHAR.repeat(word.length()));
                idx = sb.indexOf(word, idx + 1);
            }
        }
        return sb.toString();
    }

    // ==================== 静态脱敏工具方法 ====================

    /** 手机号脱敏：134****1234 */
    private static final Pattern PHONE_PATTERN = Pattern.compile("(1[3-9]\\d)\\d{4}(\\d{4})");

    public static String maskPhone(String text) {
        if (text == null) return null;
        return PHONE_PATTERN.matcher(text).replaceAll("$1****$2");
    }

    /** 邮箱脱敏：a***@example.com */
    public static String maskEmail(String text) {
        if (text == null) return null;
        return text.replaceAll("(?<=.{1}).(?=.*@)", "*");
    }

    /** 身份证脱敏：110101****1234 */
    public static String maskIdCard(String text) {
        if (text == null) return null;
        return text.replaceAll("(\\d{6})\\d{8}(\\d{4})", "$1****$2");
    }

    /**
     * 消息体自动脱敏 — 在存储/展示前调用
     */
    public String sanitize(String text) {
        if (text == null) return null;
        String result = replace(text);   // 1. 过滤敏感词
        result = maskPhone(result);      // 2. 脱敏手机号
        result = maskIdCard(result);     // 3. 脱敏身份证
        return result;
    }
}
