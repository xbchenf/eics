package com.eics.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;

/**
 * 启用 Spring Retry — 对外部服务调用（LLM / Milvus / Rasa）自动重试
 *
 * 各服务通过 @Retryable 注解配置重试策略：
 * - RasaClient.sendMessage()    最大3次，间隔 500ms / 1s / 2s
 * - RAGServiceImpl.search()     最大2次，间隔 1s / 2s
 */
@Configuration
@EnableRetry
public class RetryConfig {
}
