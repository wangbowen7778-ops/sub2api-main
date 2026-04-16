package com.sub2api.module.gateway.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sub2api.module.account.model.entity.Account;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Gemini Messages Compatibility Service
 * Gemini API 兼容性服务，处理 Gemini 请求的转换和转发
 *
 * @author Alibaba Java Code Guidelines
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GeminiMessagesCompatService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    // API URLs
    private static final String GEMINI_BASE_URL = "https://generativelanguage.googleapis.com";
    private static final String GEMINI_V1_BETA_URL = "https://generativelanguage.googleapis.com/v1beta";

    // 重试配置
    private static final int MAX_RETRIES = 5;
    private static final long RETRY_BASE_DELAY_MS = 1000;
    private static final long RETRY_MAX_DELAY_MS = 16000;

    // 粘性会话 TTL
    private static final long STICKY_SESSION_TTL_SECONDS = 3600;

    /**
     * Gemini 工具调用需要的虚拟签名
     */
    private static final String DUMMY_THOUGHT_SIGNATURE = "skip_thought_signature_validator";

    /**
     * 获取 Token Provider
     */
    public Object getTokenProvider() {
        return null;
    }

    /**
     * 转发 Gemini 请求
     */
    public ForwardResult forwardRequest(Account account, String model, Map<String, Object> requestBody, Map<String, String> headers) {
        try {
            String apiKey = getApiKey(account);
            if (apiKey == null || apiKey.isBlank()) {
                return ForwardResult.fail("No API key available", 401);
            }

            String baseUrl = GEMINI_V1_BETA_URL;
            String url = baseUrl + "/models/" + model + ":generateContent?key=" + apiKey;

            HttpHeaders httpHeaders = new HttpHeaders();
            httpHeaders.setContentType(MediaType.APPLICATION_JSON);

            // 添加原始请求头
            if (headers != null) {
                headers.forEach((key, value) -> {
                    if (isAllowedHeader(key)) {
                        httpHeaders.set(key, value);
                    }
                });
            }

            // 处理请求体 - 注入 thought signature
            Map<String, Object> processedBody = processRequestBody(requestBody);

            String jsonBody = objectMapper.writeValueAsString(processedBody);
            HttpEntity<String> entity = new HttpEntity<>(jsonBody, httpHeaders);

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

            ForwardResult result = new ForwardResult();
            result.setStatusCode(response.getStatusCode().value());
            result.setHeaders(response.getHeaders());
            result.setBody(response.getBody());
            result.setSuccess(response.getStatusCode().is2xxSuccessful());

            return result;

        } catch (Exception e) {
            log.error("Gemini request failed: {}", e.getMessage());
            return ForwardResult.fail("Request failed: " + e.getMessage(), 500);
        }
    }

    /**
     * 转发流式 Gemini 请求
     */
    public ForwardResult forwardStreamRequest(Account account, String model, Map<String, Object> requestBody, Map<String, String> headers) {
        try {
            String apiKey = getApiKey(account);
            if (apiKey == null || apiKey.isBlank()) {
                return ForwardResult.fail("No API key available", 401);
            }

            String baseUrl = GEMINI_V1_BETA_URL;
            String url = baseUrl + "/models/" + model + ":streamGenerateContent?key=" + apiKey + "&alt=sse";

            HttpHeaders httpHeaders = new HttpHeaders();
            httpHeaders.setContentType(MediaType.APPLICATION_JSON);

            if (headers != null) {
                headers.forEach((key, value) -> {
                    if (isAllowedHeader(key)) {
                        httpHeaders.set(key, value);
                    }
                });
            }

            Map<String, Object> processedBody = processRequestBody(requestBody);
            String jsonBody = objectMapper.writeValueAsString(processedBody);
            HttpEntity<String> entity = new HttpEntity<>(jsonBody, httpHeaders);

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

            ForwardResult result = new ForwardResult();
            result.setStatusCode(response.getStatusCode().value());
            result.setHeaders(response.getHeaders());
            result.setBody(response.getBody());
            result.setSuccess(response.getStatusCode().is2xxSuccessful());

            return result;

        } catch (Exception e) {
            log.error("Gemini stream request failed: {}", e.getMessage());
            return ForwardResult.fail("Request failed: " + e.getMessage(), 500);
        }
    }

    /**
     * 处理请求体 - 注入 thought signature
     */
    private Map<String, Object> processRequestBody(Map<String, Object> body) {
        if (body == null) {
            return Map.of();
        }

        try {
            // 深度复制避免修改原始对象
            String json = objectMapper.writeValueAsString(body);
            JsonNode root = objectMapper.readTree(json);

            // 如果有 contents，遍历并注入 thought signature
            if (root.has("contents")) {
                JsonNode contents = root.get("contents");
                if (contents.isArray()) {
                    for (JsonNode content : contents) {
                        injectThoughtSignature(content);
                    }
                }
            }

            // 转换回 Map
            return objectMapper.convertValue(root, Map.class);

        } catch (Exception e) {
            log.warn("Failed to process request body: {}", e.getMessage());
            return body;
        }
    }

    /**
     * 注入 thought signature
     */
    private void injectThoughtSignature(JsonNode content) {
        if (!content.has("parts")) {
            return;
        }

        JsonNode parts = content.get("parts");
        if (!parts.isArray()) {
            return;
        }

        for (JsonNode part : parts) {
            if (part.has("functionCall")) {
                JsonNode functionCall = part.get("functionCall");
                if (!functionCall.has("thoughtSignature")) {
                    try {
                        var mutableFunctionCall = objectMapper.treeToValue(functionCall, com.fasterxml.jackson.databind.node.ObjectNode.class);
                        mutableFunctionCall.put("thoughtSignature", DUMMY_THOUGHT_SIGNATURE);
                    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                        log.warn("Failed to process function call: {}", e.getMessage());
                    }
                }
            }
        }
    }

    /**
     * 检查模型是否支持
     */
    public boolean isModelSupported(String model) {
        if (model == null || model.isBlank()) {
            return false;
        }
        String lower = model.toLowerCase();
        return lower.contains("gemini") ||
                lower.contains("gemma") ||
                lower.startsWith("models/");
    }

    /**
     * 获取 API Key
     */
    private String getApiKey(Account account) {
        if (account.getCredentials() == null) {
            return null;
        }
        Object key = account.getCredentials().get("api_key");
        return key != null ? key.toString() : null;
    }

    /**
     * 检查是否允许透传该请求头
     */
    private boolean isAllowedHeader(String headerName) {
        if (headerName == null) {
            return false;
        }
        String lower = headerName.toLowerCase();
        return "content-type".equals(lower) ||
                "user-agent".equals(lower) ||
                "x-goog-api-client".equals(lower) ||
                "x-goog-api-key".equals(lower);
    }

    /**
     * 解析 Gemini 错误
     */
    public String parseError(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return "Unknown error";
        }

        try {
            JsonNode json = objectMapper.readTree(responseBody);
            if (json.has("error")) {
                JsonNode error = json.get("error");
                if (error.has("message")) {
                    return error.get("message").asText();
                }
                if (error.has("status")) {
                    return error.get("status").asText();
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse Gemini error: {}", e.getMessage());
        }

        return responseBody.length() > 100 ? responseBody.substring(0, 100) : responseBody;
    }

    /**
     * 判断是否为 Rate Limit 错误
     */
    public boolean isRateLimitError(int statusCode, String responseBody) {
        if (statusCode == 429) {
            return true;
        }

        if (responseBody != null && responseBody.contains("RESOURCE_EXHAUSTED")) {
            return true;
        }

        return false;
    }

    // ========== Result Classes ==========

    @Data
    public static class ForwardResult {
        private int statusCode;
        private HttpHeaders headers;
        private String body;
        private boolean success;
        private String error;

        public static ForwardResult success(int statusCode, String body) {
            ForwardResult result = new ForwardResult();
            result.statusCode = statusCode;
            result.body = body;
            result.success = true;
            return result;
        }

        public static ForwardResult fail(String error, int statusCode) {
            ForwardResult result = new ForwardResult();
            result.error = error;
            result.statusCode = statusCode;
            result.success = false;
            return result;
        }
    }
}
