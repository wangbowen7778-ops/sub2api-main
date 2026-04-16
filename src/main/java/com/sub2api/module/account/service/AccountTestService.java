package com.sub2api.module.account.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sub2api.module.account.model.entity.Account;
import com.sub2api.module.gateway.service.AntigravityService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Account Test Service
 * 账号连接测试服务，用于测试各平台账号的连通性
 *
 * @author Alibaba Java Code Guidelines
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountTestService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final AntigravityService antigravityService;

    // 默认测试模型
    private static final String DEFAULT_ANTHROPIC_MODEL = "claude-3-haiku-20240307";
    private static final String DEFAULT_OPENAI_MODEL = "gpt-4o-mini";
    private static final String DEFAULT_GEMINI_MODEL = "gemini-1.5-flash";
    private static final String DEFAULT_ANTIGRAVITY_MODEL = "claude-sonnet-4-20250514";

    // 超时配置
    private static final Duration TEST_TIMEOUT = Duration.ofSeconds(30);

    /**
     * 测试事件类型
     */
    @Data
    public static class TestEvent {
        private String type;
        private String text;
        private String model;
        private String status;
        private String code;
        private String imageUrl;
        private String mimeType;
        private Object data;
        private boolean success;
        private String error;
    }

    /**
     * 同步测试账号连接
     */
    public TestResult testAccount(Account account) {
        return testAccount(account, getDefaultModel(account.getPlatform()));
    }

    /**
     * 同步测试账号连接（指定模型）
     */
    public TestResult testAccount(Account account, String modelId) {
        long startTime = System.currentTimeMillis();

        try {
            String platform = account.getPlatform();
            Map<String, Object> credentials = account.getCredentials();

            if (credentials == null || credentials.isEmpty()) {
                return TestResult.fail("No credentials available", modelId, System.currentTimeMillis() - startTime);
            }

            // 根据平台类型选择测试方法
            return switch (platform != null ? platform.toLowerCase() : "") {
                case "anthropic", "claude" -> testAnthropicAccount(account, modelId, startTime);
                case "openai" -> testOpenAIAccount(account, modelId, startTime);
                case "gemini", "google" -> testGeminiAccount(account, modelId, startTime);
                case "antigravity" -> testAntigravityAccount(account, modelId, startTime);
                default -> TestResult.fail("Unsupported platform: " + platform, modelId, System.currentTimeMillis() - startTime);
            };

        } catch (Exception e) {
            log.error("Account test exception: accountId={}, error={}", account.getId(), e.getMessage());
            return TestResult.fail("Test failed: " + e.getMessage(), modelId, System.currentTimeMillis() - startTime);
        }
    }

    /**
     * 异步测试账号连接
     */
    public CompletableFuture<TestResult> testAccountAsync(Account account) {
        return CompletableFuture.supplyAsync(() -> testAccount(account));
    }

    /**
     * 异步测试账号连接（指定模型）
     */
    public CompletableFuture<TestResult> testAccountAsync(Account account, String modelId) {
        return CompletableFuture.supplyAsync(() -> testAccount(account, modelId));
    }

    // ========== Platform-specific test methods ==========

    /**
     * 测试 Anthropic 账号
     */
    private TestResult testAnthropicAccount(Account account, String modelId, long startTime) {
        try {
            Map<String, Object> credentials = account.getCredentials();
            String token = getAuthToken(account);

            String url = "https://api.anthropic.com/v1/messages";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("anthropic-version", "2023-06-01");

            if (isOAuthAccount(account)) {
                headers.set("Authorization", "Bearer " + token);
            } else {
                headers.set("x-api-key", token);
            }

            Map<String, Object> body = Map.of(
                    "model", modelId != null ? modelId : DEFAULT_ANTHROPIC_MODEL,
                    "max_tokens", 1,
                    "messages", new Object[]{
                            Map.of("role", "user", "content", "hi")
                    }
            );

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.POST, entity, String.class);

            long latency = System.currentTimeMillis() - startTime;

            if (response.getStatusCode().is2xxSuccessful()) {
                return TestResult.success(modelId, response.getBody(), latency);
            } else {
                return TestResult.fail("HTTP " + response.getStatusCode().value(), modelId, latency);
            }

        } catch (Exception e) {
            return TestResult.fail(parseError(e), modelId, System.currentTimeMillis() - startTime);
        }
    }

    /**
     * 测试 OpenAI 账号
     */
    private TestResult testOpenAIAccount(Account account, String modelId, long startTime) {
        try {
            String token = getAuthToken(account);
            String url = "https://api.openai.com/v1/models";

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + token);

            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, String.class);

            long latency = System.currentTimeMillis() - startTime;

            if (response.getStatusCode().is2xxSuccessful()) {
                return TestResult.success("gpt-4o", response.getBody(), latency);
            } else {
                return TestResult.fail("HTTP " + response.getStatusCode().value(), modelId, latency);
            }

        } catch (Exception e) {
            return TestResult.fail(parseError(e), modelId, System.currentTimeMillis() - startTime);
        }
    }

    /**
     * 测试 Gemini 账号
     */
    private TestResult testGeminiAccount(Account account, String modelId, long startTime) {
        try {
            String apiKey = getApiKey(account);
            String url = "https://generativelanguage.googleapis.com/v1/models?key=" + apiKey;

            HttpHeaders headers = new HttpHeaders();
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, String.class);

            long latency = System.currentTimeMillis() - startTime;

            if (response.getStatusCode().is2xxSuccessful()) {
                return TestResult.success(modelId != null ? modelId : DEFAULT_GEMINI_MODEL, response.getBody(), latency);
            } else {
                return TestResult.fail("HTTP " + response.getStatusCode().value(), modelId, latency);
            }

        } catch (Exception e) {
            return TestResult.fail(parseError(e), modelId, System.currentTimeMillis() - startTime);
        }
    }

    /**
     * 测试 Antigravity 账号
     */
    private TestResult testAntigravityAccount(Account account, String modelId, long startTime) {
        try {
            String token = getAuthToken(account);

            String url = "https://api.antigravity.dev/v1/messages";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("anthropic-version", "2023-06-01");

            if (isOAuthAccount(account)) {
                headers.set("Authorization", "Bearer " + token);
            } else {
                headers.set("x-api-key", token);
            }

            Map<String, Object> body = Map.of(
                    "model", modelId != null ? modelId : DEFAULT_ANTIGRAVITY_MODEL,
                    "max_tokens", 1,
                    "messages", new Object[]{
                            Map.of("role", "user", "content", "hi")
                    }
            );

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.POST, entity, String.class);

            long latency = System.currentTimeMillis() - startTime;

            if (response.getStatusCode().is2xxSuccessful()) {
                return TestResult.success(modelId, response.getBody(), latency);
            } else {
                return TestResult.fail("HTTP " + response.getStatusCode().value(), modelId, latency);
            }

        } catch (Exception e) {
            return TestResult.fail(parseError(e), modelId, System.currentTimeMillis() - startTime);
        }
    }

    // ========== Helper methods ==========

    /**
     * 获取认证 Token
     */
    private String getAuthToken(Account account) {
        if (account.getCredentials() == null) {
            return "";
        }

        Object accessToken = account.getCredentials().get("access_token");
        if (accessToken != null) {
            return accessToken.toString();
        }

        Object apiKey = account.getCredentials().get("api_key");
        if (apiKey != null) {
            return apiKey.toString();
        }

        return "";
    }

    /**
     * 获取 API Key
     */
    private String getApiKey(Account account) {
        if (account.getCredentials() == null) {
            return "";
        }

        Object apiKey = account.getCredentials().get("api_key");
        return apiKey != null ? apiKey.toString() : "";
    }

    /**
     * 判断是否为 OAuth 账号
     */
    private boolean isOAuthAccount(Account account) {
        return "oauth".equalsIgnoreCase(account.getType()) ||
                "setup_token".equalsIgnoreCase(account.getType());
    }

    /**
     * 获取默认测试模型
     */
    private String getDefaultModel(String platform) {
        if (platform == null) {
            return DEFAULT_ANTHROPIC_MODEL;
        }
        return switch (platform.toLowerCase()) {
            case "anthropic", "claude" -> DEFAULT_ANTHROPIC_MODEL;
            case "openai" -> DEFAULT_OPENAI_MODEL;
            case "gemini", "google" -> DEFAULT_GEMINI_MODEL;
            case "antigravity" -> DEFAULT_ANTIGRAVITY_MODEL;
            default -> DEFAULT_ANTHROPIC_MODEL;
        };
    }

    /**
     * 解析错误信息
     */
    private String parseError(Exception e) {
        String message = e.getMessage();
        if (message == null) {
            return "Unknown error";
        }

        // 尝试解析 JSON 错误
        if (message.contains("{") && message.contains("error")) {
            try {
                JsonNode json = objectMapper.readTree(message);
                if (json.has("error")) {
                    JsonNode error = json.get("error");
                    if (error.has("message")) {
                        return error.get("message").asText();
                    }
                    if (error.has("type")) {
                        return error.get("type").asText();
                    }
                }
            } catch (Exception ignored) {
            }
        }

        // 简化常见错误
        if (message.contains("401")) {
            return "Authentication failed (401)";
        }
        if (message.contains("403")) {
            return "Access forbidden (403)";
        }
        if (message.contains("429")) {
            return "Rate limited (429)";
        }
        if (message.contains("500")) {
            return "Server error (500)";
        }
        if (message.contains("Connection refused")) {
            return "Connection refused";
        }
        if (message.contains("Timeout")) {
            return "Request timeout";
        }

        return message.length() > 100 ? message.substring(0, 100) + "..." : message;
    }

    // ========== Result classes ==========

    @Data
    public static class TestResult {
        private boolean success;
        private String error;
        private String model;
        private String responseText;
        private long latencyMs;

        public static TestResult success(String model, String responseText, long latencyMs) {
            TestResult result = new TestResult();
            result.success = true;
            result.model = model;
            result.responseText = responseText;
            result.latencyMs = latencyMs;
            return result;
        }

        public static TestResult fail(String error, String model, long latencyMs) {
            TestResult result = new TestResult();
            result.success = false;
            result.error = error;
            result.model = model;
            result.latencyMs = latencyMs;
            return result;
        }
    }
}
