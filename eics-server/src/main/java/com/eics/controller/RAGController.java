package com.eics.controller;

import com.eics.common.Result;
import com.eics.service.RAGService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "RAG 知识库问答", description = "向量检索 + LLM 生成答案，Rasa Action 调用")
@Slf4j
@RestController
@RequestMapping("/api/v1/rag")
@RequiredArgsConstructor
public class RAGController {

    private final RAGService ragService;

    /**
     * RAG 问答接口
     * Rasa Action 通过 HTTP POST 调用此接口获取知识库答案
     */
    @Operation(summary = "RAG 问答", description = "输入问题，返回 LLM 基于知识库生成的答案 + 溯源文档")
    @PostMapping("/chat")
    public Result<Map<String, Object>> chat(@RequestBody Map<String, String> request) {
        String question = request.get("question");
        String sessionId = request.getOrDefault("session_id", "default");

        log.info("RAG 问答请求: sessionId={}, question={}", sessionId, question);
        Map<String, Object> result = ragService.chat(question, sessionId);

        if ((boolean) result.getOrDefault("fallback", false)) {
            return Result.ok("未找到相关知识，正在转接人工坐席", result);
        }
        return Result.ok(result);
    }

    /**
     * 知识库检索（不含 LLM 生成）
     */
    @GetMapping("/search")
    public Result<Map<String, Object>> search(
            @RequestParam String question,
            @RequestParam(defaultValue = "5") int topK) {
        return Result.ok(Map.of("results", ragService.search(question, topK)));
    }
}
