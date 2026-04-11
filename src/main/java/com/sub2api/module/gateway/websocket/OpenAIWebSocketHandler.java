package com.sub2api.module.gateway.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sub2api.module.account.service.AccountSelector;
import com.sub2api.module.account.service.AccountService;
import com.sub2api.module.billing.service.RateLimitService;
import com.sub2api.module.gateway.service.ProxyService;
import com.sub2api.module.gateway.service.ProxyService.ProxyRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OpenAI WebSocket 处理器
 * 处理 OpenAI API 的 WebSocket 连接
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OpenAIWebSocketHandler extends TextWebSocketHandler {

    private final ProxyService proxyService;
    private final RateLimitService rateLimitService;
    private final AccountService accountService;
    private final AccountSelector accountSelector;
    private final ObjectMapper objectMapper;

    // 会话存储
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    // 会话上下文存储
    private final Map<String, SessionContext> sessionContexts = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.put(session.getId(), session);
        log.info("WebSocket 连接建立: sessionId={}", session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        try {
            String payload = message.getPayload();
            log.debug("WebSocket 消息: sessionId={}, payload={}", session.getId(), payload);

            // 解析消息
            Map<String, Object> request = objectMapper.readValue(payload, Map.class);
            String type = (String) request.get("type");

            if ("response.create".equals(type)) {
                handleResponseCreate(session, request);
            } else if ("input_token".equals(type)) {
                handleInputToken(session, request);
            } else if ("previous_response".equals(type)) {
                handlePreviousResponse(session, request);
            } else {
                log.warn("未知的消息类型: sessionId={}, type={}", session.getId(), type);
            }
        } catch (Exception e) {
            log.error("处理 WebSocket 消息失败: sessionId={}, error={}", session.getId(), e.getMessage());
            sendError(session, "处理消息失败: " + e.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sessions.remove(session.getId());
        sessionContexts.remove(session.getId());
        log.info("WebSocket 连接关闭: sessionId={}, status={}", session.getId(), status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("WebSocket 传输错误: sessionId={}, error={}", session.getId(), exception.getMessage());
        sessions.remove(session.getId());
        sessionContexts.remove(session.getId());
    }

    /**
     * 处理 response.create 请求
     */
    private void handleResponseCreate(WebSocketSession session, Map<String, Object> request) throws Exception {
        // 提取模型和消息
        String model = extractModel(request);
        Map<String, Object> body = buildChatCompletionBody(request);

        // 创建代理请求
        ProxyRequest proxyRequest = new ProxyRequest();
        proxyRequest.setPlatform("openai");
        proxyRequest.setPath("/v1/chat/completions");
        proxyRequest.setBody(body);
        proxyRequest.setModel(model);
        proxyRequest.setStream(true);
        proxyRequest.setSessionId(session.getId());

        // 获取客户端 IP
        String clientIp = extractClientIp(session);
        proxyRequest.setClientIp(clientIp);

        // 限流检查
        String rpmKey = "ws:user:" + session.getId() + ":rpm";
        rateLimitService.checkRpm(rpmKey, 60);

        // 发送请求并处理响应
        proxyService.proxyStreamRequest(proxyRequest)
                .doOnNext(chunk -> {
                    try {
                        if (session.isOpen()) {
                            // 转换 SSE 格式为 WebSocket 格式
                            String wsMessage = convertToWebSocketMessage(chunk);
                            session.sendMessage(new TextMessage(wsMessage));
                        }
                    } catch (Exception e) {
                        log.error("发送 WebSocket 消息失败: sessionId={}, error={}", session.getId(), e.getMessage());
                    }
                })
                .doOnError(e -> {
                    log.error("代理请求失败: sessionId={}, error={}", session.getId(), e.getMessage());
                    try {
                        sendError(session, "请求失败: " + e.getMessage());
                    } catch (Exception ex) {
                        log.error("发送错误消息失败: sessionId={}", session.getId());
                    }
                })
                .doOnComplete(() -> {
                    log.info("WebSocket 请求完成: sessionId={}", session.getId());
                    try {
                        sendCompletion(session);
                    } catch (Exception e) {
                        log.error("发送完成消息失败: sessionId={}", session.getId());
                    }
                })
                .subscribe();

        // 存储会话上下文
        SessionContext ctx = new SessionContext();
        ctx.setSessionId(session.getId());
        ctx.setModel(model);
        ctx.setCreatedAt(java.time.LocalDateTime.now());
        sessionContexts.put(session.getId(), ctx);
    }

    /**
     * 处理 input_token 请求
     */
    private void handleInputToken(WebSocketSession session, Map<String, Object> request) {
        // 可以用于预计算或验证输入 token
        log.debug("处理 input_token: sessionId={}", session.getId());
    }

    /**
     * 处理 previous_response 请求
     */
    private void handlePreviousResponse(WebSocketSession session, Map<String, Object> request) {
        // 用于上下文延续或错误恢复
        log.debug("处理 previous_response: sessionId={}", session.getId());
    }

    /**
     * 提取模型名称
     */
    private String extractModel(Map<String, Object> request) {
        Object model = request.get("model");
        return model != null ? model.toString() : "gpt-4o";
    }

    /**
     * 从 WebSocket 消息构建 Chat Completion 请求体
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> buildChatCompletionBody(Map<String, Object> request) {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("model", request.get("model"));
        body.put("messages", request.getOrDefault("messages", java.util.Collections.emptyList()));
        body.put("stream", true);

        // 复制其他可选参数
        if (request.containsKey("temperature")) {
            body.put("temperature", request.get("temperature"));
        }
        if (request.containsKey("max_tokens")) {
            body.put("max_tokens", request.get("max_tokens"));
        }
        if (request.containsKey("top_p")) {
            body.put("top_p", request.get("top_p"));
        }

        return body;
    }

    /**
     * 提取客户端 IP
     */
    private String extractClientIp(WebSocketSession session) {
        Map<String, Object> attrs = session.getAttributes();
        if (attrs != null && attrs.containsKey("clientIp")) {
            return attrs.get("clientIp").toString();
        }
        return "unknown";
    }

    /**
     * 转换 SSE chunk 为 WebSocket 消息格式
     */
    private String convertToWebSocketMessage(String chunk) {
        try {
            // 解析 SSE 格式: data: {"id":"...","choices":[...]}
            if (chunk.startsWith("data: ")) {
                chunk = chunk.substring(6);
            }
            if ("[DONE]".equals(chunk.trim())) {
                return "{\"type\":\"done\"}";
            }
            return chunk;
        } catch (Exception e) {
            log.warn("转换消息格式失败: {}", e.getMessage());
            return chunk;
        }
    }

    /**
     * 发送错误消息
     */
    private void sendError(WebSocketSession session, String errorMessage) throws Exception {
        if (session.isOpen()) {
            String error = objectMapper.writeValueAsString(Map.of(
                    "type", "error",
                    "error", Map.of(
                            "type", "api_error",
                            "message", errorMessage
                    )
            ));
            session.sendMessage(new TextMessage(error));
        }
    }

    /**
     * 发送完成消息
     */
    private void sendCompletion(WebSocketSession session) throws Exception {
        if (session.isOpen()) {
            String done = objectMapper.writeValueAsString(Map.of(
                    "type", "response.done",
                    "response", Map.of("done", true)
            ));
            session.sendMessage(new TextMessage(done));
        }
    }

    /**
     * 会话上下文
     */
    @lombok.Data
    public static class SessionContext {
        private String sessionId;
        private String model;
        private java.time.LocalDateTime createdAt;
        private Long userId;
        private Long apiKeyId;
        private Long groupId;
    }
}
