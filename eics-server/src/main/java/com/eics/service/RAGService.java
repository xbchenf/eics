package com.eics.service;

import java.util.List;
import java.util.Map;

/**
 * RAG 知识库问答服务 — 向量检索 + LLM 生成答案
 */
public interface RAGService {

    /**
     * RAG 问答
     * @param question   用户问题
     * @param sessionId  会话 ID（用于记录对话上下文）
     * @return { answer, sources: [{title, text}] }
     */
    Map<String, Object> chat(String question, String sessionId);

    /**
     * 向量检索（不含 LLM 生成，仅返回相关切片）
     * @param question 用户问题
     * @param topK     返回 Top-K 结果
     * @return 相关切片列表
     */
    List<Map<String, Object>> search(String question, int topK);
}
