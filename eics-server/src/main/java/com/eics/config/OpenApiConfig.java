package com.eics.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * SpringDoc OpenAPI 配置 — 自动生成 API 文档
 * 文档地址: http://localhost:8080/swagger-ui.html
 * JSON 描述: http://localhost:8080/v3/api-docs
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI eicsOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("EICS 企业一体化智能客服系统 API")
                        .description("""
                                基于 SpringBoot + SpringAI + Rasa 的私有化智能客服平台。

                                模块说明:
                                - /api/v1/rag/**   RAG 知识库问答
                                - /api/v1/order/** 客服工单管理
                                - /api/v1/agent/** 人工坐席转接与接待
                                - /api/v1/session/** 会话管理
                                - /api/v1/doc/**   知识库文档管理
                                - /api/v1/auth/**  坐席认证

                                鉴权说明:
                                - 标注 🔓 的接口为公开接口（Rasa 调用或用户端）
                                - 标注 🔒 的接口需要坐席登录，请求头携带 Authorization: Bearer {token}
                                """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("EICS Team")));
    }
}
