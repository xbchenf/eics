package com.eics.config;

import com.eics.auth.AuthInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/v1/**")
                .excludePathPatterns(
                        "/api/v1/health",
                        "/api/v1/auth/**",
                        "/api/v1/rag/chat",
                        "/api/v1/agent/transfer",
                        "/api/v1/agent/user-message",
                        "/api/v1/agent/messages/**",
                        "/api/v1/order/create",
                        "/api/v1/session/*/messages",
                        "/api/v1/dialog/**"
                );
    }
}
