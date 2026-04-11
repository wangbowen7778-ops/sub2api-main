package com.sub2api.module.auth.websocket;

import com.sub2api.module.apikey.model.vo.ApiKeyInfo;
import com.sub2api.module.apikey.service.ApiKeyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * WebSocket 认证拦截器
 * 在 WebSocket 连接建立前验证 API Key
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthHandshakeInterceptor implements HandshakeInterceptor {

    private static final String API_KEY_HEADER = "X-API-Key";
    private static final String API_KEY_QUERY_PARAM = "api_key";

    private final ApiKeyService apiKeyService;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                    WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {
        String apiKey = extractApiKey(request);

        if (apiKey == null) {
            log.warn("WebSocket 认证失败: 缺少 API Key");
            return false;
        }

        try {
            // 验证 API Key
            ApiKeyInfo apiKeyInfo = apiKeyService.validateApiKey(apiKey);

            if (apiKeyInfo == null) {
                log.warn("WebSocket 认证失败: 无效的 API Key");
                return false;
            }

            // 将用户信息存储到 WebSocket 会话属性中
            attributes.put("userId", apiKeyInfo.getUserId());
            attributes.put("apiKeyId", apiKeyInfo.getKeyId());
            attributes.put("groupId", apiKeyInfo.getGroupId());
            attributes.put("apiKeyInfo", apiKeyInfo);

            // 设置客户端 IP
            if (request instanceof ServletServerHttpRequest servletRequest) {
                String clientIp = getClientIp(servletRequest);
                attributes.put("clientIp", clientIp);
            }

            log.debug("WebSocket 认证成功: userId={}, apiKeyId={}", apiKeyInfo.getUserId(), apiKeyInfo.getKeyId());
            return true;

        } catch (Exception e) {
            log.error("WebSocket 认证异常: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        if (exception != null) {
            log.error("WebSocket 握手后异常: {}", exception.getMessage());
        }
    }

    /**
     * 从请求中提取 API Key
     */
    private String extractApiKey(ServerHttpRequest request) {
        // 从 Header 提取
        String apiKey = request.getHeaders().getFirst(API_KEY_HEADER);
        if (StringUtils.hasText(apiKey)) {
            return apiKey;
        }

        // 从 Query 参数提取
        if (request.getURI() != null) {
            String query = request.getURI().getQuery();
            if (StringUtils.hasText(query)) {
                for (String param : query.split("&")) {
                    if (param.startsWith(API_KEY_QUERY_PARAM + "=")) {
                        return param.substring(API_KEY_QUERY_PARAM.length() + 1);
                    }
                }
            }
        }

        return null;
    }

    /**
     * 获取客户端 IP
     */
    private String getClientIp(ServletServerHttpRequest request) {
        String xForwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
        if (StringUtils.hasText(xForwardedFor)) {
            return xForwardedFor.split(",")[0].trim();
        }

        String xRealIp = request.getHeaders().getFirst("X-Real-IP");
        if (StringUtils.hasText(xRealIp)) {
            return xRealIp;
        }

        if (request.getServletRequest() != null) {
            return request.getServletRequest().getRemoteAddr();
        }

        return "unknown";
    }
}
