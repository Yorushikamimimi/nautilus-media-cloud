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

    @Value("${nautilus.auth.token:change-me}")
    private String requireToken;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        // 放行 OPTIONS 预检请求
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        // 放行 Worker 节点的 /pending 和 /status 接口 (本例中假设 Worker 和 Backend
        // 是内部安全域，不需要复杂鉴权，或者是固定放行)
        // 为了安全起见，我们暂时要求所有调用都带 token，除了 Worker 节点的 /pending?workerNode=Elma-Node-01
        // 但更简单的方式是：针对前端的请求(比如 /create, /list, /delete 等)必须校验 Token。
        // 这里采用一刀切简单口令校验：Header 里拿 Authorization
        String token = request.getHeader("Authorization");

        // 去除 Bearer 前缀 (如果有)
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7).trim();
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
