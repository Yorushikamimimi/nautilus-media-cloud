package com.nautilus.dispatch.config;

import com.nautilus.common.core.domain.AjaxResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.PrintWriter;

/**
 * 访问鉴权拦截器
 * 拦截没有或携带错误 Token 的请求 (仅针对 /api/v1 路径)
 */
@Slf4j
@Component
public class AuthInterceptor implements HandlerInterceptor {

    @Value("${nautilus.auth.token:changeme}")
    private String requireToken;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        // 放行 OPTIONS 预检请求
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        // 演示用：所有 /api/**（除 WebMvcConfig 排除项）需 Header Authorization: Bearer <token>，
        // 或 URL query param ?token=<token>（兼容浏览器 EventSource SSE 不支持自定义请求头）。
        // 与 nautilus.auth.token / 环境变量 NAUTILUS_AUTH_TOKEN 一致。
        String token = request.getHeader("Authorization");

        // 去除 Bearer 前缀 (如果有)
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7).trim();
        }

        // Header 无 token 时，fallback 到 query param（SSE EventSource 场景）
        if (token == null || token.isEmpty()) {
            token = request.getParameter("token");
        }

        if (requireToken.equals(token)) {
            return true;
        }

        // 鉴权失败，返回 401 JSON
        log.warn("思想犯 - 拦截到未授权访问，IP: {}", request.getRemoteAddr());
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);

        AjaxResult result = AjaxResult.error(401, "思想犯 - 访问口令无效或已过期，请重新验明正身");

        try (PrintWriter writer = response.getWriter()) {
            writer.write(objectMapper.writeValueAsString(result));
            writer.flush();
        }
        return false;
    }
}
