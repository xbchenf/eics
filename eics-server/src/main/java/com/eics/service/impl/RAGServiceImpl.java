package com.eics.service.impl;

import com.eics.mapper.CsChatMessageMapper;
import com.eics.mapper.CsChatSessionMapper;
import com.eics.mapper.EkDocumentChunkMapper;
import com.eics.mapper.EkDocumentMapper;
import com.eics.service.RAGService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * RAG 知识库问答实现 — 外部调用带重试机制
 */
@Slf4j
@Service
public class RAGServiceImpl implements RAGService {

    private final VectorStore vectorStore;
    private final ChatClient chatClient;
    private final EkDocumentChunkMapper chunkMapper;
    private final EkDocumentMapper documentMapper;
    private final CsChatSessionMapper sessionMapper;
    private final CsChatMessageMapper messageMapper;

    public RAGServiceImpl(VectorStore vectorStore,
                          ChatClient.Builder chatClientBuilder,
                          EkDocumentChunkMapper chunkMapper,
                          EkDocumentMapper documentMapper,
                          CsChatSessionMapper sessionMapper,
                          CsChatMessageMapper messageMapper) {
        this.vectorStore = vectorStore;
        this.chatClient = chatClientBuilder.build();
        this.chunkMapper = chunkMapper;
        this.documentMapper = documentMapper;
        this.sessionMapper = sessionMapper;
        this.messageMapper = messageMapper;
    }

    @Override
    public Map<String, Object> chat(String question, String sessionId) {
        // 1. 向量检索 Top-K 相关切片
        List<Map<String, Object>> sources = search(question, 5);

        // 2. 无结果 → 兜底话术
        if (sources.isEmpty()) {
            return Map.of(
                    "answer", "",
                    "sources", Collections.emptyList(),
                    "fallback", true
            );
        }

        // 3. 拼接上下文，调用 LLM 生成答案
        StringBuilder context = new StringBuilder();
        for (int i = 0; i < sources.size(); i++) {
            @SuppressWarnings("unchecked")
            Map<String, Object> s = sources.get(i);
            context.append("【参考资料").append(i + 1).append("】\n")
                    .append(s.get("content")).append("\n\n");
        }

        String prompt = """
                你是一个企业智能客服助手。请根据以下参考资料回答用户问题。
                要求：
                1. 仅基于参考资料回答，不要编造信息
                2. 如果参考资料不足以回答，请明确说明"参考资料中未找到相关信息"
                3. 回答简洁、清晰、专业

                参考资料：
                %s

                用户问题：%s
                """.formatted(context.toString(), question);

        String answer;
        try {
            answer = chatClient.prompt().user(prompt).call().content();
        } catch (Exception e) {
            log.error("LLM 调用失败: {}", e.getMessage(), e);
            return Map.of(
                    "answer", "",
                    "sources", Collections.emptyList(),
                    "fallback", true
            );
        }

        // 4. 构造溯源信息
        List<Map<String, Object>> sourceList = sources.stream()
                .map(s -> Map.of("title", s.getOrDefault("title", "未知文档"),
                        "text", s.getOrDefault("content", "")))
                .toList();

        return Map.of("answer", answer, "sources", sourceList, "fallback", false);
    }

    @Retryable(retryFor = Exception.class, maxAttempts = 2,
               backoff = @Backoff(delay = 1000))
    @Override
    public List<Map<String, Object>> search(String question, int topK) {
        // SpringAI VectorStore 相似性检索 — 异常自动重试
        var request = org.springframework.ai.vectorstore.SearchRequest
                .query(question)
                .withTopK(topK);
        var documents = vectorStore.similaritySearch(request);

        if (documents == null || documents.isEmpty()) {
            return Collections.emptyList();
        }

        List<Map<String, Object>> results = new ArrayList<>();
        for (var doc : documents) {
            // 过滤已删除文档（Milvus 中的向量可能未同步清理）
            String docIdStr = null;
            if (doc.getMetadata() != null) {
                Object idObj = doc.getMetadata().getOrDefault("document_id", "");
                docIdStr = idObj != null ? idObj.toString() : "";
            }
            if (docIdStr != null && !docIdStr.isEmpty()) {
                try {
                    Long docId = Long.parseLong(docIdStr);
                    var ekDoc = documentMapper.selectById(docId);
                    if (ekDoc == null) continue;  // 文档不存在或被逻辑删除，跳过
                } catch (NumberFormatException ignored) {
                }
            }
            Map<String, Object> item = new HashMap<>();
            item.put("content", doc.getContent());
            if (doc.getMetadata() != null) {
                Object idObj = doc.getMetadata().getOrDefault("document_id", "");
                docIdStr = idObj != null ? idObj.toString() : "";
                item.put("documentId", docIdStr);
                item.put("score", doc.getMetadata().getOrDefault("distance", 0));
            }
            // SpringAI 1.0.0-M4 Milvus metadata 编码 bug workaround
            String title = "未知文档";
            if (docIdStr != null && !docIdStr.isEmpty()) {
                try {
                    Long docId = Long.parseLong(docIdStr);
                    var ekDoc = documentMapper.selectById(docId);
                    if (ekDoc != null) {
                        title = ekDoc.getTitle();
                    }
                } catch (NumberFormatException ignored) {
                }
            }
            item.put("title", title);
            results.add(item);
        }
        return results;
    }
}
