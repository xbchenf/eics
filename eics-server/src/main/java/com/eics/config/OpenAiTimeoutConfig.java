package com.eics.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 修复 SpringAI 1.0.0-M4 不支持 read-timeout/connect-timeout 配置的问题
 * 手动创建带超时的 RestClient.Builder
 */
@Configuration
public class OpenAiTimeoutConfig {

    @Bean
    public RestClient.Builder restClientBuilder() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(30_000);  // 30 秒连接超时
        factory.setReadTimeout(120_000);    // 120 秒读取超时（LLM 生成大段回答需要时间）
        return RestClient.builder().requestFactory(factory);
    }
}
