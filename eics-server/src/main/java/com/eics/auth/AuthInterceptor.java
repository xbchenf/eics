package com.eics.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;

/**
 * 认证拦截器 — 校验 Bearer Token
 * 仅拦截 WebMvcConfig 中指定的路径，白名单路径由 WebMvcConfig 排除
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        // 放行 OPTIONS 预检请求
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            write401(response, "未登录或Token已过期");
            return false;
        }

        String token = authHeader.substring(7);
        Map<String, Object> claims = jwtUtil.verifyToken(token);
        if (claims == null) {
            write401(response, "Token无效或已过期");
            return false;
        }

        // 统一注入 userId（AgentController 等旧代码仍读 agentId，做兼容映射）
        Object uidObj = claims.get("agentId");
        Long userId = uidObj instanceof Number ? ((Number) uidObj).longValue() : null;
        request.setAttribute("userId", userId);
        request.setAttribute("agentId", userId);             // 旧代码兼容
        request.setAttribute("agentUsername", claims.get("username"));
        request.setAttribute("agentRole", claims.get("role"));
        request.setAttribute("userRole", claims.get("role"));
        return true;
    }

    private void write401(HttpServletResponse response, String message) throws Exception {
        response.setStatus(401);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":401,\"message\":\"" + message + "\"}");
    }
}
