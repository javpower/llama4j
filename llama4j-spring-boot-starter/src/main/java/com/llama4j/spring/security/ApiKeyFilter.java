package com.llama4j.spring.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * API Key 安全校验过滤器 — Bearer Token 认证
 *
 * <p>对 {@code /v1/**} 路径下的请求进行 API Key 校验。
 * 客户端需在请求头中携带 {@code Authorization: Bearer <api-key>}。</p>
 *
 * <p>如果未配置 {@code llama4j.api.key}，则不启用校验（开发模式）。</p>
 */
public class ApiKeyFilter extends OncePerRequestFilter {

    private static final Logger LOG = LoggerFactory.getLogger(ApiKeyFilter.class);

    private final String apiKey;

    public ApiKeyFilter(String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();

        // 只对 /v1/ 路径做校验
        if (!path.startsWith("/v1/")) {
            filterChain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            LOG.warn("API 请求缺少 Authorization 头: {} {}", request.getMethod(), path);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":{\"message\":\"Missing or invalid Authorization header\",\"type\":\"authentication_error\"}}");
            return;
        }

        String token = authHeader.substring(7).trim();
        if (!apiKey.equals(token)) {
            LOG.warn("API Key 验证失败: {} {}", request.getMethod(), path);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":{\"message\":\"Invalid API key\",\"type\":\"authentication_error\"}}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * 是否启用 — apiKey 为 null 或空时不启用。
     */
    public static boolean isEnabled(String apiKey) {
        return apiKey != null && !apiKey.isBlank();
    }
}
