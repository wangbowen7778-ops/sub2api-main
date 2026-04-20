package com.sub2api.module.gateway.controller;

import com.sub2api.module.apikey.model.vo.ApiKeyInfo;
import com.sub2api.module.apikey.service.ApiKeyService;
import com.sub2api.module.billing.service.UsageLogService;
import com.sub2api.module.gateway.model.vo.ModelInfo;
import com.sub2api.module.gateway.service.ProxyService;
import com.sub2api.module.gateway.service.ProxyService.ProxyRequest;
import com.sub2api.module.user.model.entity.User;
import com.sub2api.module.user.service.UserService;
import com.sub2api.module.common.model.vo.Result;
import com.sub2api.module.common.util.IpUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * API 网关控制器
 *
 * @author Alibaba Java Code Guidelines
 */
@Tag(name = "网关", description = "API 网关代理接口")
@Slf4j
@RestController
@RequiredArgsConstructor
public class GatewayController {

    private final ProxyService proxyService;
    private final UserService userService;
    private final ApiKeyService apiKeyService;
    private final UsageLogService usageLogService;

    @Operation(summary = "Claude 兼容 API - 消息流")
    @PostMapping(value = "/v1/messages", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter handleMessages(
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {

        SseEmitter emitter = new SseEmitter(300000L);

        try {
            Long userId = getUserId(request);
            Long groupId = getGroupId(request);
            String platform = "anthropic";
            String clientIp = IpUtil.getRealIp(request);

            ProxyRequest proxyRequest = buildProxyRequest(userId, groupId, platform, "/v1/messages", body, request);
            proxyRequest.setClientIp(clientIp);
            proxyRequest.setStream(true);

            proxyService.proxyStreamRequest(proxyRequest)
                    .doOnNext(chunk -> {
                        try {
                            emitter.send(SseEmitter.event().data(chunk));
                        } catch (Exception e) {
                            emitter.completeWithError(e);
                        }
                    })
                    .doOnError(e -> {
                        log.error("流式响应异常: {}", e.getMessage());
                        emitter.completeWithError(e);
                    })
                    .doOnComplete(emitter::complete)
                    .subscribe();

        } catch (Exception e) {
            log.error("处理消息请求失败: {}", e.getMessage());
            emitter.completeWithError(e);
        }

        return emitter;
    }

    @Operation(summary = "OpenAI 兼容 API - Chat Completions")
    @PostMapping("/v1/chat/completions")
    public ResponseEntity<Map<String, Object>> handleChatCompletions(
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {

        Long userId = getUserId(request);
        Long groupId = getGroupId(request);
        String platform = "openai";
        String clientIp = IpUtil.getRealIp(request);

        ProxyRequest proxyRequest = buildProxyRequest(userId, groupId, platform, "/v1/chat/completions", body, request);
        proxyRequest.setClientIp(clientIp);

        Map<String, Object> result = proxyService.proxyRequest(proxyRequest);
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "OpenAI 兼容 API - Responses")
    @PostMapping("/v1/responses")
    public ResponseEntity<Map<String, Object>> handleResponses(
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {

        Long userId = getUserId(request);
        Long groupId = getGroupId(request);
        String platform = "openai";
        String clientIp = IpUtil.getRealIp(request);

        ProxyRequest proxyRequest = buildProxyRequest(userId, groupId, platform, "/v1/responses", body, request);
        proxyRequest.setClientIp(clientIp);

        Map<String, Object> result = proxyService.proxyRequest(proxyRequest);
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "OpenAI 兼容 API - Embeddings")
    @PostMapping("/v1/embeddings")
    public ResponseEntity<Map<String, Object>> handleEmbeddings(
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {

        Long userId = getUserId(request);
        Long groupId = getGroupId(request);
        String platform = "openai";
        String clientIp = IpUtil.getRealIp(request);

        ProxyRequest proxyRequest = buildProxyRequest(userId, groupId, platform, "/v1/embeddings", body, request);
        proxyRequest.setClientIp(clientIp);

        Map<String, Object> result = proxyService.proxyRequest(proxyRequest);
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Gemini 兼容 API")
    @PostMapping("/v1beta/**")
    public ResponseEntity<Map<String, Object>> handleGemini(
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {

        String path = request.getRequestURI();
        Long userId = getUserId(request);
        Long groupId = getGroupId(request);
        String platform = "google";
        String clientIp = IpUtil.getRealIp(request);

        ProxyRequest proxyRequest = buildProxyRequest(userId, groupId, platform, path, body, request);
        proxyRequest.setClientIp(clientIp);

        Map<String, Object> result = proxyService.proxyRequest(proxyRequest);
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Antigravity API")
    @PostMapping("/antigravity/**")
    public ResponseEntity<Map<String, Object>> handleAntigravity(
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {

        String path = request.getRequestURI();
        Long userId = getUserId(request);
        Long groupId = getGroupId(request);
        String platform = "antigravity";
        String clientIp = IpUtil.getRealIp(request);

        ProxyRequest proxyRequest = buildProxyRequest(userId, groupId, platform, path, body, request);
        proxyRequest.setClientIp(clientIp);

        Map<String, Object> result = proxyService.proxyRequest(proxyRequest);
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "获取可用模型列表")
    @GetMapping("/v1/models")
    public ResponseEntity<Map<String, Object>> handleModels(HttpServletRequest request) {
        Long groupId = getGroupId(request);
        String platform = getPlatformFromAuth(request);

        // 获取可用模型
        List<String> availableModels = proxyService.getAvailableModels(groupId, platform);

        if (availableModels != null && !availableModels.isEmpty()) {
            // 从 model_mapping 构建模型列表
            List<ModelInfo> models = availableModels.stream()
                    .map(modelId -> new ModelInfo(modelId, "model", modelId, "2024-01-01T00:00:00Z"))
                    .collect(Collectors.toList());

            return ResponseEntity.ok(Map.of(
                    "object", "list",
                    "data", models
            ));
        }

        // Fallback to default models
        if ("openai".equals(platform)) {
            return ResponseEntity.ok(Map.of(
                    "object", "list",
                    "data", getDefaultOpenAIModels()
            ));
        }

        return ResponseEntity.ok(Map.of(
                "object", "list",
                "data", getDefaultAnthropicModels()
        ));
    }

    /**
     * 获取认证中的平台信息
     */
    private String getPlatformFromAuth(HttpServletRequest request) {
        Object auth = request.getAttribute("org.springframework.security.core.Authentication");
        if (auth instanceof ApiKeyInfo apiKeyInfo && apiKeyInfo.getGroupId() != null) {
            // 从分组获取平台信息
            return "anthropic"; // 默认
        }
        return "anthropic";
    }

    /**
     * 获取默认的 Anthropic 模型列表
     */
    private List<ModelInfo> getDefaultAnthropicModels() {
        return List.of(
                new ModelInfo("claude-3-5-sonnet-20241022", "model", "claude-3-5-sonnet-20241022", "2024-01-01T00:00:00Z"),
                new ModelInfo("claude-3-opus-20240229", "model", "claude-3-opus-20240229", "2024-01-01T00:00:00Z"),
                new ModelInfo("claude-3-haiku-20240307", "model", "claude-3-haiku-20240307", "2024-01-01T00:00:00Z"),
                new ModelInfo("claude-2-1-20240522", "model", "claude-2-1-20240522", "2024-01-01T00:00:00Z"),
                new ModelInfo("claude-2-0-20240307", "model", "claude-2-0-20240307", "2024-01-01T00:00:00Z"),
                new ModelInfo("claude-instant-20240307", "model", "claude-instant-20240307", "2024-01-01T00:00:00Z")
        );
    }

    /**
     * 获取默认的 OpenAI 模型列表
     */
    private List<ModelInfo> getDefaultOpenAIModels() {
        return List.of(
                new ModelInfo("gpt-4o", "model", "gpt-4o", "2024-01-01T00:00:00Z"),
                new ModelInfo("gpt-4o-mini", "model", "gpt-4o-mini", "2024-01-01T00:00:00Z"),
                new ModelInfo("gpt-4-turbo", "model", "gpt-4-turbo", "2024-01-01T00:00:00Z"),
                new ModelInfo("gpt-4", "model", "gpt-4", "2024-01-01T00:00:00Z"),
                new ModelInfo("gpt-3.5-turbo", "model", "gpt-3.5-turbo", "2024-01-01T00:00:00Z")
        );
    }

    @Operation(summary = "获取 API Key 用量信息")
    @GetMapping("/v1/usage")
    public ResponseEntity<Map<String, Object>> handleUsage(HttpServletRequest request) {
        Long userId = getUserId(request);
        Long apiKeyId = getApiKeyId(request);

        if (apiKeyId == null) {
            return ResponseEntity.ok(Map.of(
                    "error", Map.of(
                            "type", "authentication_error",
                            "message", "Invalid API key"
                    )
            ));
        }

        // 获取 API Key 信息
        var apiKeyOpt = apiKeyService.getById(apiKeyId);
        if (apiKeyOpt == null) {
            return ResponseEntity.ok(Map.of(
                    "error", Map.of(
                            "type", "authentication_error",
                            "message", "Invalid API key"
                    )
            ));
        }

        var apiKey = apiKeyOpt;
        boolean isQuotaLimited = apiKey.getQuota() != null && apiKey.getQuota().compareTo(BigDecimal.ZERO) > 0
                || hasRateLimits(apiKey);

        if (isQuotaLimited) {
            return buildQuotaLimitedResponse(apiKey);
        } else {
            return buildUnrestrictedResponse(apiKey, userId);
        }
    }

    /**
     * 判断是否有速率限制
     */
    private boolean hasRateLimits(com.sub2api.module.apikey.model.entity.ApiKey apiKey) {
        return (apiKey.getRateLimit5h() != null && apiKey.getRateLimit5h().compareTo(BigDecimal.ZERO) > 0)
                || (apiKey.getRateLimit1d() != null && apiKey.getRateLimit1d().compareTo(BigDecimal.ZERO) > 0)
                || (apiKey.getRateLimit7d() != null && apiKey.getRateLimit7d().compareTo(BigDecimal.ZERO) > 0);
    }

    /**
     * 构建 quota_limited 模式的响应
     */
    private ResponseEntity<Map<String, Object>> buildQuotaLimitedResponse(com.sub2api.module.apikey.model.entity.ApiKey apiKey) {
        Map<String, Object> resp = new java.util.HashMap<>();
        resp.put("mode", "quota_limited");
        resp.put("isValid", "active".equals(apiKey.getStatus()) || "quota_exhausted".equals(apiKey.getStatus()) || "expired".equals(apiKey.getStatus()));
        resp.put("status", apiKey.getStatus());

        // 总额度信息
        if (apiKey.getQuota() != null && apiKey.getQuota().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal remaining = apiKey.getQuota().subtract(apiKey.getQuotaUsed() != null ? apiKey.getQuotaUsed() : BigDecimal.ZERO);
            resp.put("quota", Map.of(
                    "limit", apiKey.getQuota(),
                    "used", apiKey.getQuotaUsed() != null ? apiKey.getQuotaUsed() : BigDecimal.ZERO,
                    "remaining", remaining,
                    "unit", "USD"
            ));
            resp.put("remaining", remaining);
            resp.put("unit", "USD");
        }

        // 速率限制信息
        List<Map<String, Object>> rateLimits = new ArrayList<>();
        if (apiKey.getRateLimit5h() != null && apiKey.getRateLimit5h().compareTo(BigDecimal.ZERO) > 0) {
            Map<String, Object> entry = new java.util.HashMap<>();
            entry.put("window", "5h");
            entry.put("limit", apiKey.getRateLimit5h());
            entry.put("used", apiKey.getUsage5h() != null ? apiKey.getUsage5h() : BigDecimal.ZERO);
            BigDecimal remaining5h = apiKey.getRateLimit5h().subtract(apiKey.getUsage5h() != null ? apiKey.getUsage5h() : BigDecimal.ZERO);
            entry.put("remaining", remaining5h.max(BigDecimal.ZERO));
            if (apiKey.getWindow5hStart() != null) {
                entry.put("window_start", apiKey.getWindow5hStart());
                OffsetDateTime resetAt = apiKey.getWindow5hStart().plusHours(5);
                if (resetAt.isAfter(OffsetDateTime.now())) {
                    entry.put("reset_at", resetAt);
                }
            }
            rateLimits.add(entry);
        }
        if (apiKey.getRateLimit1d() != null && apiKey.getRateLimit1d().compareTo(BigDecimal.ZERO) > 0) {
            Map<String, Object> entry = new java.util.HashMap<>();
            entry.put("window", "1d");
            entry.put("limit", apiKey.getRateLimit1d());
            entry.put("used", apiKey.getUsage1d() != null ? apiKey.getUsage1d() : BigDecimal.ZERO);
            BigDecimal remaining1d = apiKey.getRateLimit1d().subtract(apiKey.getUsage1d() != null ? apiKey.getUsage1d() : BigDecimal.ZERO);
            entry.put("remaining", remaining1d.max(BigDecimal.ZERO));
            if (apiKey.getWindow1dStart() != null) {
                entry.put("window_start", apiKey.getWindow1dStart());
                OffsetDateTime resetAt = apiKey.getWindow1dStart().plusDays(1);
                if (resetAt.isAfter(OffsetDateTime.now())) {
                    entry.put("reset_at", resetAt);
                }
            }
            rateLimits.add(entry);
        }
        if (apiKey.getRateLimit7d() != null && apiKey.getRateLimit7d().compareTo(BigDecimal.ZERO) > 0) {
            Map<String, Object> entry = new java.util.HashMap<>();
            entry.put("window", "7d");
            entry.put("limit", apiKey.getRateLimit7d());
            entry.put("used", apiKey.getUsage7d() != null ? apiKey.getUsage7d() : BigDecimal.ZERO);
            BigDecimal remaining7d = apiKey.getRateLimit7d().subtract(apiKey.getUsage7d() != null ? apiKey.getUsage7d() : BigDecimal.ZERO);
            entry.put("remaining", remaining7d.max(BigDecimal.ZERO));
            if (apiKey.getWindow7dStart() != null) {
                entry.put("window_start", apiKey.getWindow7dStart());
                OffsetDateTime resetAt = apiKey.getWindow7dStart().plusDays(7);
                if (resetAt.isAfter(OffsetDateTime.now())) {
                    entry.put("reset_at", resetAt);
                }
            }
            rateLimits.add(entry);
        }
        if (!rateLimits.isEmpty()) {
            resp.put("rate_limits", rateLimits);
        }

        // 过期时间
        if (apiKey.getExpiresAt() != null) {
            resp.put("expires_at", apiKey.getExpiresAt());
            long daysUntilExpiry = ChronoUnit.DAYS.between(OffsetDateTime.now(), apiKey.getExpiresAt());
            resp.put("days_until_expiry", daysUntilExpiry);
        }

        // 用量数据
        Map<String, Object> usageData = buildUsageData(apiKey.getId());
        if (usageData != null) {
            resp.put("usage", usageData);
        }

        return ResponseEntity.ok(resp);
    }

    /**
     * 构建 unrestricted 模式的响应
     */
    private ResponseEntity<Map<String, Object>> buildUnrestrictedResponse(com.sub2api.module.apikey.model.entity.ApiKey apiKey, Long userId) {
        Map<String, Object> resp = new java.util.HashMap<>();
        resp.put("mode", "unrestricted");
        resp.put("isValid", true);

        // 获取用户余额
        if (userId != null) {
            var userOpt = userService.findById(userId);
            if (userOpt != null) {
                resp.put("planName", "钱包余额");
                resp.put("remaining", userOpt.getBalance() != null ? userOpt.getBalance() : BigDecimal.ZERO);
                resp.put("unit", "USD");
                resp.put("balance", userOpt.getBalance() != null ? userOpt.getBalance() : BigDecimal.ZERO);
            }
        }

        // 用量数据
        Map<String, Object> usageData = buildUsageData(apiKey.getId());
        if (usageData != null) {
            resp.put("usage", usageData);
        }

        return ResponseEntity.ok(resp);
    }

    /**
     * 构建用量数据
     */
    private Map<String, Object> buildUsageData(Long apiKeyId) {
        try {
            // 今日用量
            OffsetDateTime todayStart = OffsetDateTime.now().toLocalDate().atStartOfDay().atOffset(ZoneOffset.UTC);
            var todayStats = usageLogService.getApiKeyStatistics(apiKeyId, todayStart, OffsetDateTime.now());

            // 总用量
            var totalStats = usageLogService.getApiKeyStatistics(apiKeyId, null, null);

            Map<String, Object> today = new java.util.HashMap<>();
            today.put("requests", todayStats.requestCount());
            today.put("input_tokens", todayStats.inputTokens());
            today.put("output_tokens", todayStats.outputTokens());
            today.put("total_tokens", todayStats.inputTokens() + todayStats.outputTokens());
            today.put("cost", todayStats.cost());

            Map<String, Object> total = new java.util.HashMap<>();
            total.put("requests", totalStats.requestCount());
            total.put("input_tokens", totalStats.inputTokens());
            total.put("output_tokens", totalStats.outputTokens());
            total.put("total_tokens", totalStats.inputTokens() + totalStats.outputTokens());
            total.put("cost", totalStats.cost());

            Map<String, Object> usage = new java.util.HashMap<>();
            usage.put("today", today);
            usage.put("total", total);

            return usage;
        } catch (Exception e) {
            log.warn("获取用量数据失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 获取 API Key ID
     */
    private Long getApiKeyId(HttpServletRequest request) {
        Object auth = request.getAttribute("org.springframework.security.core.Authentication");
        if (auth instanceof ApiKeyInfo apiKeyInfo) {
            return apiKeyInfo.getKeyId();
        }
        return null;
    }

    /**
     * 获取用户 ID
     */
    private Long getUserId(HttpServletRequest request) {
        Object auth = request.getAttribute("org.springframework.security.core.Authentication");
        if (auth != null) {
            if (auth instanceof User user) {
                return user.getId();
            }
            if (auth instanceof ApiKeyInfo apiKeyInfo) {
                return apiKeyInfo.getUserId();
            }
            if (auth instanceof Long) {
                return (Long) auth;
            }
        }
        return null;
    }

    /**
     * 获取分组 ID
     */
    private Long getGroupId(HttpServletRequest request) {
        Object auth = request.getAttribute("org.springframework.security.core.Authentication");
        if (auth instanceof ApiKeyInfo apiKeyInfo && apiKeyInfo.getGroupId() != null) {
            return apiKeyInfo.getGroupId();
        }
        return 1L;
    }

    /**
     * 构建代理请求
     */
    private ProxyRequest buildProxyRequest(Long userId, Long groupId, String platform,
                                           String path, Map<String, Object> body, HttpServletRequest request) {
        ProxyRequest proxyRequest = new ProxyRequest();
        proxyRequest.setUserId(userId);
        proxyRequest.setGroupId(groupId);
        proxyRequest.setPlatform(platform);
        proxyRequest.setPath(path);
        proxyRequest.setBody(body);
        proxyRequest.setSessionId(request.getHeader("X-Session-Id"));

        // 提取模型
        String model = extractModel(body, platform);
        proxyRequest.setModel(model);

        return proxyRequest;
    }

    /**
     * 提取模型名称
     */
    private String extractModel(Map<String, Object> body, String platform) {
        if (body == null) {
            return "unknown";
        }

        if ("anthropic".equals(platform)) {
            Object model = body.get("model");
            return model != null ? model.toString() : "claude-3-5-sonnet-20241022";
        }
        if ("openai".equals(platform)) {
            Object model = body.get("model");
            return model != null ? model.toString() : "gpt-4o";
        }
        if ("google".equals(platform) || "antigravity".equals(platform)) {
            Object model = body.get("model");
            return model != null ? model.toString() : "gemini-pro";
        }
        return "unknown";
    }
}
