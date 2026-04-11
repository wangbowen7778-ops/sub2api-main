package com.sub2api.module.auth.filter;

import com.sub2api.module.apikey.service.ApiKeyCacheService;
import com.sub2api.module.apikey.service.ApiKeyService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

/**
 * API Key 认证过滤器
 *
 * @author Alibaba Java Code Guidelines
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final String API_KEY_HEADER = "X-API-Key";
    private static final String API_KEY_QUERY_PARAM = "api_key";

    private final ApiKeyService apiKeyService;
    private final ApiKeyCacheService apiKeyCacheService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        // 如果已经认证过，直接放行
        if (SecurityContextHolder.getContext().getAuthentication() != null
                && SecurityContextHolder.getContext().getAuthentication().isAuthenticated()) {
            filterChain.doFilter(request, response);
            return;
        }

        String apiKey = extractApiKey(request);
        if (apiKey == null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            // 从缓存或数据库获取 API Key 信息
            var apiKeyInfo = apiKeyCacheService.getApiKeyInfo(apiKey);
            if (apiKeyInfo == null) {
                apiKeyInfo = apiKeyService.validateApiKey(apiKey);
            }

            if (apiKeyInfo != null && apiKeyInfo.isEnabled()) {
                // 创建认证令牌
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        apiKeyInfo.getUserId(),
                        null,
                        Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
                );
                authentication.setDetails(request);

                SecurityContextHolder.getContext().setAuthentication(authentication);

                // 将 API Key 信息设置到请求属性中
                request.setAttribute("apiKeyInfo", apiKeyInfo);

                log.debug("API Key 认证成功: keyId={}", apiKeyInfo.getKeyId());
            }
        } catch (Exception e) {
            log.debug("API Key 认证失败: {}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    /**
     * 从请求中提取 API Key
     */
    private String extractApiKey(HttpServletRequest request) {
        // 从 Header 提取
        String apiKey = request.getHeader(API_KEY_HEADER);
        if (StringUtils.hasText(apiKey)) {
            return apiKey;
        }

        // 从 Query 参数提取
        apiKey = request.getParameter(API_KEY_QUERY_PARAM);
        if (StringUtils.hasText(apiKey)) {
            return apiKey;
        }

        return null;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        // 仅在网关路径进行 API Key 认证
        return !path.startsWith("/v1/") &&
                !path.startsWith("/v1beta/") &&
                !path.startsWith("/antigravity/");
    }
}
