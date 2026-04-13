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
 * OpenAI Gateway Service
 * OpenAI API 网关服务，处理 ChatGPT/Codex 请求
 *
 * @author Alibaba Java Code Guidelines
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OpenAIGatewayService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    // API URLs
    private static final String CHATGPT_CODEX_URL = "https://chatgpt.com/backend-api/codex/responses";
    private static final String OPENAI_PLATFORM_API_URL = "https://api.openai.com/v1/responses";

    // 粘性会话 TTL
    private static final long STICKY_SESSION_TTL_SECONDS = 3600;

    /**
     * Codex 使用量快照
     */
    @Data
    public static class CodexUsageSnapshot {
        private Double primaryUsedPercent;
        private Integer primaryResetAfterSeconds;
        private Integer primaryWindowMinutes;
        private Double secondaryUsedPercent;
        private Integer secondaryResetAfterSeconds;
        private Integer secondaryWindowMinutes;
        private Double primaryOverSecondaryPercent;
        private String updatedAt;
    }

    /**
     * 标准化的 Codex 限额
     */
    @Data
    public static class NormalizedCodexLimits {
        private Double used5hPercent;
        private Integer reset5hSeconds;
        private Integer window5hMinutes;
        private Double used7dPercent;
        private Integer reset7dSeconds;
        private Integer window7dMinutes;
    }

    /**
     * 转发请求结果
     */
    @Data
    public static class ForwardResult {
        private int statusCode;
        private HttpHeaders headers;
        private String body;
        private CodexUsageSnapshot codexUsage;
        private boolean success;
        private String error;
    }

    /**
     * 获取 Token Provider
     */
    public Object getTokenProvider() {
        // 返回 token provider 实例（如果有）
        return null;
    }

    /**
     * 转发 ChatGPT Codex 请求（OAuth 账号）
     */
    public ForwardResult forwardCodexRequest(Account account, String action, Map<String, Object> body, Map<String, String> headers) {
        try {
            String accessToken = getAccessToken(account);
            if (accessToken == null || accessToken.isBlank()) {
                return ForwardResult.fail("No access token available", 401);
            }

            String url = CHATGPT_CODEX_URL;
            HttpHeaders httpHeaders = new HttpHeaders();
            httpHeaders.setContentType(MediaType.APPLICATION_JSON);
            httpHeaders.set("Authorization", "Bearer " + accessToken);

            // 添加原始请求头
            if (headers != null) {
                headers.forEach((key, value) -> {
                    if (isAllowedHeader(key)) {
                        httpHeaders.set(key, value);
                    }
                });
            }

            String jsonBody = objectMapper.writeValueAsString(body);
            HttpEntity<String> entity = new HttpEntity<>(jsonBody, httpHeaders);

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

            ForwardResult result = new ForwardResult();
            result.setStatusCode(response.getStatusCode().value());
            result.setHeaders(response.getHeaders());
            result.setBody(response.getBody());
            result.setSuccess(response.getStatusCode().is2xxSuccessful());

            // 解析 Codex 使用量
            CodexUsageSnapshot usage = parseCodexUsage(response.getHeaders());
            result.setCodexUsage(usage);

            return result;

        } catch (Exception e) {
            log.error("Codex request failed: {}", e.getMessage());
            return ForwardResult.fail("Request failed: " + e.getMessage(), 500);
        }
    }

    /**
     * 转发 OpenAI Platform API 请求（API Key 账号）
     */
    public ForwardResult forwardOpenAIRequest(Account account, String action, Map<String, Object> body, Map<String, String> headers) {
        try {
            String apiKey = getApiKey(account);
            if (apiKey == null || apiKey.isBlank()) {
                return ForwardResult.fail("No API key available", 401);
            }

            String url = OPENAI_PLATFORM_API_URL;
            HttpHeaders httpHeaders = new HttpHeaders();
            httpHeaders.setContentType(MediaType.APPLICATION_JSON);
            httpHeaders.set("Authorization", "Bearer " + apiKey);

            // 添加原始请求头
            if (headers != null) {
                headers.forEach((key, value) -> {
                    if (isAllowedHeader(key)) {
                        httpHeaders.set(key, value);
                    }
                });
            }

            String jsonBody = objectMapper.writeValueAsString(body);
            HttpEntity<String> entity = new HttpEntity<>(jsonBody, httpHeaders);

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

            ForwardResult result = new ForwardResult();
            result.setStatusCode(response.getStatusCode().value());
            result.setHeaders(response.getHeaders());
            result.setBody(response.getBody());
            result.setSuccess(response.getStatusCode().is2xxSuccessful());

            return result;

        } catch (Exception e) {
            log.error("OpenAI request failed: {}", e.getMessage());
            return ForwardResult.fail("Request failed: " + e.getMessage(), 500);
        }
    }

    /**
     * 通用转发请求
     */
    public ForwardResult forward(Account account, String action, Map<String, Object> body, Map<String, String> headers) {
        if (isOAuthAccount(account)) {
            return forwardCodexRequest(account, action, body, headers);
        } else {
            return forwardOpenAIRequest(account, action, body, headers);
        }
    }

    /**
     * 解析 Codex 使用量
     */
    private CodexUsageSnapshot parseCodexUsage(HttpHeaders headers) {
        CodexUsageSnapshot snapshot = new CodexUsageSnapshot();

        // 从响应头解析使用量信息
        String primaryUsed = headers.getFirst("x-ray-ai primary-used-percent");
        String primaryReset = headers.getFirst("x-ray-ai primary-reset-after-seconds");
        String primaryWindow = headers.getFirst("x-ray-ai primary-window-minutes");
        String secondaryUsed = headers.getFirst("x-ray-ai secondary-used-percent");
        String secondaryReset = headers.getFirst("x-ray-ai secondary-reset-after-seconds");
        String secondaryWindow = headers.getFirst("x-ray-ai secondary-window-minutes");

        if (primaryUsed != null) {
            snapshot.setPrimaryUsedPercent(parseDouble(primaryUsed));
        }
        if (primaryReset != null) {
            snapshot.setPrimaryResetAfterSeconds(parseInt(primaryReset));
        }
        if (primaryWindow != null) {
            snapshot.setPrimaryWindowMinutes(parseInt(primaryWindow));
        }
        if (secondaryUsed != null) {
            snapshot.setSecondaryUsedPercent(parseDouble(secondaryUsed));
        }
        if (secondaryReset != null) {
            snapshot.setSecondaryResetAfterSeconds(parseInt(secondaryReset));
        }
        if (secondaryWindow != null) {
            snapshot.setSecondaryWindowMinutes(parseInt(secondaryWindow));
        }

        return snapshot;
    }

    /**
     * 标准化 Codex 限额
     */
    public NormalizedCodexLimits normalizeCodexLimits(CodexUsageSnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }

        NormalizedCodexLimits limits = new NormalizedCodexLimits();

        int primaryMins = snapshot.getPrimaryWindowMinutes() != null ? snapshot.getPrimaryWindowMinutes() : 0;
        int secondaryMins = snapshot.getSecondaryWindowMinutes() != null ? snapshot.getSecondaryWindowMinutes() : 0;

        // 根据 window 分钟数判断哪个是 5h，哪个是 7d
        if (primaryMins <= 300) { // 5h = 300min
            limits.setUsed5hPercent(snapshot.getPrimaryUsedPercent());
            limits.setReset5hSeconds(snapshot.getPrimaryResetAfterSeconds());
            limits.setWindow5hMinutes(primaryMins);
            limits.setUsed7dPercent(snapshot.getSecondaryUsedPercent());
            limits.setReset7dSeconds(snapshot.getSecondaryResetAfterSeconds());
            limits.setWindow7dMinutes(secondaryMins);
        } else {
            // 交换
            limits.setUsed5hPercent(snapshot.getSecondaryUsedPercent());
            limits.setReset5hSeconds(snapshot.getSecondaryResetAfterSeconds());
            limits.setWindow5hMinutes(secondaryMins);
            limits.setUsed7dPercent(snapshot.getPrimaryUsedPercent());
            limits.setReset7dSeconds(snapshot.getPrimaryResetAfterSeconds());
            limits.setWindow7dMinutes(primaryMins);
        }

        return limits;
    }

    /**
     * 检查模型是否支持
     */
    public boolean isModelSupported(String model) {
        if (model == null || model.isBlank()) {
            return false;
        }
        String lower = model.toLowerCase();
        return lower.contains("gpt-") ||
                lower.contains("chatgpt") ||
                lower.contains("codex") ||
                lower.contains("o1") ||
                lower.contains("o3") ||
                lower.contains("o4");
    }

    /**
     * 检查是否为 Codex CLI 请求
     */
    public boolean isCodexCLIRequest(Map<String, String> headers) {
        if (headers == null) {
            return false;
        }
        String ua = headers.get("User-Agent");
        return ua != null && ua.contains("codex_cli");
    }

    /**
     * 获取访问令牌
     */
    private String getAccessToken(Account account) {
        if (account.getCredentials() == null) {
            return null;
        }
        Object token = account.getCredentials().get("access_token");
        return token != null ? token.toString() : null;
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
     * 判断是否为 OAuth 账号
     */
    private boolean isOAuthAccount(Account account) {
        return "oauth".equalsIgnoreCase(account.getType());
    }

    /**
     * 检查是否允许透传该请求头
     */
    private boolean isAllowedHeader(String headerName) {
        if (headerName == null) {
            return false;
        }
        String lower = headerName.toLowerCase();
        return "accept-language".equals(lower) ||
                "content-type".equals(lower) ||
                "conversation_id".equals(lower) ||
                "user-agent".equals(lower) ||
                "originator".equals(lower) ||
                "session_id".equals(lower) ||
                "x-codex-turn-state".equals(lower) ||
                "x-codex-turn-metadata".equals(lower) ||
                "openai-beta".equals(lower);
    }

    /**
     * 解析 double
     */
    private Double parseDouble(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 解析 int
     */
    private Integer parseInt(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ========== Result Classes ==========

    @Data
    public static class ForwardResult {
        private int statusCode;
        private HttpHeaders headers;
        private String body;
        private CodexUsageSnapshot codexUsage;
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
