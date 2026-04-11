package com.sub2api.module.gateway.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sub2api.module.account.model.entity.Account;
import com.sub2api.module.account.model.enums.Platform;
import com.sub2api.module.account.service.AccountRefreshService;
import com.sub2api.module.account.service.AccountSelector;
import com.sub2api.module.account.service.AccountService;
import com.sub2api.module.apikey.model.vo.ApiKeyInfo;
import com.sub2api.module.apikey.service.ApiKeyService;
import com.sub2api.module.billing.model.entity.UsageLog;
import com.sub2api.module.billing.service.BillingCalculator;
import com.sub2api.module.billing.service.RateLimitService;
import com.sub2api.module.billing.service.UsageLogService;
import com.sub2api.module.channel.service.ChannelService;
import com.sub2api.module.channel.service.ChannelService.ChannelMappingResult;
import com.sub2api.module.common.exception.BusinessException;
import com.sub2api.module.common.model.enums.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 代理转发服务
 * 核心网关功能，负责请求转发、故障转移、负载均衡
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProxyService {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final AccountService accountService;
    private final AccountSelector accountSelector;
    private final RateLimitService rateLimitService;
    private final UsageLogService usageLogService;
    private final ApiKeyService apiKeyService;
    private final BillingCalculator billingCalculator;
    private final ChannelService channelService;
    private final ConcurrencyService concurrencyService;

    // 最大重试次数
    private static final int MAX_RETRY_COUNT = 2;
    // 故障转移重试延迟 (ms)
    private static final long FAILOVER_RETRY_DELAY_MS = 500;

    /**
     * 代理请求 - 带故障转移
     */
    public Map<String, Object> proxyRequest(ProxyRequest request) {
        long startTime = System.currentTimeMillis();
        ConcurrencyService.SlotResult accountSlot = null;
        ConcurrencyService.SlotResult userSlot = null;

        try {
            // 1. 限流检查
            checkRateLimit(request);

            // 2. 解析渠道映射
            ChannelMappingResult channelMapping = resolveChannelMapping(request);

            // 3. 选择账号 (带重试和故障转移)
            Account account = selectAccountWithFailover(request, channelMapping);

            // 4. 并发控制 - 获取账号槽位
            int maxConcurrent = account.getConcurrency() != null ? account.getConcurrency() : 10;
            accountSlot = concurrencyService.tryAcquireWithRelease(account.getId(), maxConcurrent);
            if (!accountSlot.acquired()) {
                throw new BusinessException(ErrorCode.ACCOUNT_ALL_UNAVAILABLE, "账号并发数已达上限");
            }

            // 用户级并发控制（如果提供了 userId）
            if (request.getUserId() != null) {
                userSlot = concurrencyService.tryAcquireWithRelease(request.getUserId(), 100); // 用户默认最大100并发
                // 用户级超限不阻止请求，仅记录
            }

            // 5. 构建转发请求
            String upstreamUrl = buildUpstreamUrl(account, request);

            // 6. 发送请求 (带重试)
            Map<String, Object> response = sendRequestWithRetry(upstreamUrl, request, account, channelMapping);

            // 7. 记录用量
            recordUsage(request, account, response, startTime);

            return response;
        } finally {
            // 释放并发槽位
            if (accountSlot != null && accountSlot.releaseFunc() != null) {
                accountSlot.releaseFunc().run();
            }
            if (userSlot != null && userSlot.releaseFunc() != null) {
                userSlot.releaseFunc().run();
            }
        }
    }

    /**
     * 代理流式请求 (SSE)
     */
    public Flux<String> proxyStreamRequest(ProxyRequest request) {
        // 1. 限流检查
        checkRateLimit(request);

        // 2. 选择账号
        Account account = selectAccount(request);

        // 3. 获取并发槽位
        ConcurrencyService.SlotResult accountSlot = concurrencyService.tryAcquireWithRelease(
                account.getId(), account.getConcurrency() != null ? account.getConcurrency() : 10);
        if (!accountSlot.acquired()) {
            return Flux.error(new BusinessException(ErrorCode.ACCOUNT_ALL_UNAVAILABLE, "账号并发数已达上限"));
        }

        // 用户级并发控制
        ConcurrencyService.SlotResult userSlot = null;
        if (request.getUserId() != null) {
            userSlot = concurrencyService.tryAcquireWithRelease(request.getUserId(), 100);
        }

        // 4. 构建转发请求
        String upstreamUrl = buildUpstreamUrl(account, request);

        // 5. 发送流式请求，并在完成时释放槽位
        return sendStreamRequest(upstreamUrl, request, account)
                .doOnTerminate(() -> {
                    // 释放并发槽位
                    if (accountSlot.releaseFunc() != null) {
                        accountSlot.releaseFunc().run();
                    }
                    if (userSlot != null && userSlot.releaseFunc() != null) {
                        userSlot.releaseFunc().run();
                    }
                })
                .doOnError(e -> {
                    log.error("流式请求异常: accountId={}, error={}", account.getId(), e.getMessage());
                    handleProxyError(account, e);
                });
    }

    /**
     * 限流检查
     */
    private void checkRateLimit(ProxyRequest request) {
        if (request.getUserId() != null) {
            String rpmKey = "user:" + request.getUserId() + ":rpm";
            rateLimitService.checkRpm(rpmKey, 60);
        }
    }

    /**
     * 选择账号
     */
    private Account selectAccount(ProxyRequest request) {
        AccountSelector.Strategy strategy = AccountSelector.Strategy.PRIORITY;

        if (request.getGroupId() != null) {
            return accountSelector.selectAccount(request.getGroupId(), strategy, request.getSessionId());
        }

        // 按平台选择
        return accountSelector.selectAccountByPlatform(request.getPlatform(), strategy, request.getSessionId());
    }

    /**
     * 解析渠道映射
     */
    private ChannelMappingResult resolveChannelMapping(ProxyRequest request) {
        if (request.getGroupId() == null) {
            ChannelMappingResult result = new ChannelMappingResult();
            result.setMappedModel(request.getModel());
            result.setChannelId(0L);
            result.setMapped(false);
            result.setBillingModelSource("requested");
            return result;
        }
        return channelService.resolveChannelMapping(request.getGroupId(), request.getModel());
    }

    /**
     * 选择账号 - 带故障转移
     */
    private Account selectAccountWithFailover(ProxyRequest request, ChannelMappingResult channelMapping) {
        int retryCount = 0;
        Account lastFailedAccount = null;

        while (retryCount <= MAX_RETRY_COUNT) {
            try {
                Account account;
                if (request.getGroupId() != null) {
                    account = accountSelector.selectAccount(request.getGroupId(),
                            AccountSelector.Strategy.PRIORITY, request.getSessionId());
                } else {
                    account = accountSelector.selectAccountByPlatform(request.getPlatform(),
                            AccountSelector.Strategy.PRIORITY, request.getSessionId());
                }

                // 如果上次失败的账号不是当前账号，重置其状态
                if (lastFailedAccount != null && !lastFailedAccount.getId().equals(account.getId())) {
                    accountService.clearRateLimited(lastFailedAccount.getId());
                }

                return account;
            } catch (BusinessException e) {
                if (e.getCode() == ErrorCode.ACCOUNT_ALL_UNAVAILABLE.getCode() && retryCount < MAX_RETRY_COUNT) {
                    retryCount++;
                    log.warn("无可用账号，{}ms 后重试 (尝试 {}/{})", FAILOVER_RETRY_DELAY_MS, retryCount, MAX_RETRY_COUNT);
                    try {
                        Thread.sleep(FAILOVER_RETRY_DELAY_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                } else {
                    throw e;
                }
            }
        }

        throw new BusinessException(ErrorCode.ACCOUNT_ALL_UNAVAILABLE);
    }

    /**
     * 发送请求 - 带重试
     */
    private Map<String, Object> sendRequestWithRetry(String upstreamUrl, ProxyRequest request,
                                                     Account account, ChannelMappingResult channelMapping) {
        int retryCount = 0;

        while (retryCount <= MAX_RETRY_COUNT) {
            try {
                return sendRequest(upstreamUrl, request, account);
            } catch (BusinessException e) {
                if (isRetryableError(e) && retryCount < MAX_RETRY_COUNT) {
                    retryCount++;
                    log.warn("请求失败，{}ms 后重试 (尝试 {}/{}): {}",
                            FAILOVER_RETRY_DELAY_MS, retryCount, MAX_RETRY_COUNT, e.getMessage());

                    // 标记当前账号为临时不可调度
                    handleRetryableError(account, e);

                    try {
                        Thread.sleep(FAILOVER_RETRY_DELAY_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                } else {
                    throw e;
                }
            }
        }

        throw new BusinessException(ErrorCode.GATEWAY_PROXY_FAIL, "请求失败，已达到最大重试次数");
    }

    /**
     * 判断错误是否可重试
     */
    private boolean isRetryableError(BusinessException e) {
        String code = e.getCode();
        return ErrorCode.GATEWAY_TIMEOUT.getCode().equals(code) ||
                ErrorCode.GATEWAY_UPSTREAM_ERROR.getCode().equals(code);
    }

    /**
     * 处理可重试的错误
     */
    private void handleRetryableError(Account account, BusinessException e) {
        // 根据错误类型设置账号状态
        if (ErrorCode.GATEWAY_TIMEOUT.getCode().equals(e.getCode())) {
            // 超时错误，设置为临时不可调度
            accountService.setTempUnschedulable(account.getId(),
                    LocalDateTime.now().plusMinutes(1), "请求超时");
        }
        // 其他可重试错误可以添加更多处理逻辑
    }

    /**
     * 处理故障转移
     */
    private Account handleFailover(ProxyRequest request, Account failedAccount, Throwable e) {
        log.warn("账号 {} 故障，进行故障转移: {}", failedAccount.getId(), e.getMessage());

        // 清理粘性会话
        if (request.getSessionId() != null && !request.getSessionId().isEmpty()) {
            // 粘性会话会在下次选择时自动重新绑定
        }

        // 设置临时不可调度状态
        accountService.setTempUnschedulable(failedAccount.getId(),
                LocalDateTime.now().plusMinutes(1), "故障转移: " + e.getMessage());

        // 尝试选择下一个可用账号
        try {
            return selectAccount(request);
        } catch (BusinessException be) {
            if (be.getCode() == ErrorCode.ACCOUNT_ALL_UNAVAILABLE.getCode()) {
                throw be;
            }
            throw be;
        }
    }

    /**
     * 构建上游 URL
     */
    private String buildUpstreamUrl(Account account, ProxyRequest request) {
        Platform platform = Platform.fromId(request.getPlatform());
        String baseUrl = platform != null ? platform.getBaseUrl() : "https://api.anthropic.com";
        return baseUrl + request.getPath();
    }

    /**
     * 发送 HTTP 请求
     */
    private Map<String, Object> sendRequest(String upstreamUrl, ProxyRequest request, Account account) {
        String apiKey = accountService.getDecryptedApiKey(account);
        if (apiKey == null) {
            throw new BusinessException(ErrorCode.ACCOUNT_CREDENTIAL_INVALID);
        }

        try {
            String response = webClient.post()
                    .uri(upstreamUrl)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .header("anthropic-version", "2023-06-01")
                    .bodyValue(request.getBody())
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(request.getTimeout() != null ? request.getTimeout() : 120))
                    .block();

            @SuppressWarnings("unchecked")
            Map<String, Object> responseMap = response != null ?
                    objectMapper.readValue(response, Map.class) : new HashMap<>();

            // 更新账号最后使用时间
            accountService.updateLastUsed(account.getId());

            return responseMap;
        } catch (WebClientResponseException e) {
            log.error("上游服务返回错误: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            handleHttpError(account, e);
            throw new BusinessException(ErrorCode.GATEWAY_UPSTREAM_ERROR,
                    "上游服务错误: " + e.getStatusCode());
        } catch (Exception e) {
            log.error("代理请求异常: {}", e.getMessage());
            throw new BusinessException(ErrorCode.GATEWAY_TIMEOUT, "请求超时");
        }
    }

    /**
     * 发送流式请求
     */
    private Flux<String> sendStreamRequest(String upstreamUrl, ProxyRequest request, Account account) {
        String apiKey = accountService.getDecryptedApiKey(account);
        if (apiKey == null) {
            return Flux.error(new BusinessException(ErrorCode.ACCOUNT_CREDENTIAL_INVALID));
        }

        return webClient.post()
                .uri(upstreamUrl)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .header("anthropic-version", "2023-06-01")
                .header("Accept", "text/event-stream")
                .bodyValue(request.getBody())
                .retrieve()
                .bodyToFlux(String.class)
                .timeout(Duration.ofSeconds(request.getTimeout() != null ? request.getTimeout() : 300))
                .doOnNext(chunk -> {
                    // 更新最后使用时间
                    accountService.updateLastUsed(account.getId());
                })
                .doOnError(e -> log.error("流式请求异常: {}", e.getMessage()));
    }

    /**
     * 处理代理错误
     */
    private void handleProxyError(Account account, Throwable e) {
        if (e instanceof WebClientResponseException wcre) {
            handleHttpError(account, wcre);
        }
    }

    /**
     * 处理 HTTP 错误
     */
    private void handleHttpError(Account account, WebClientResponseException e) {
        int statusCode = e.getStatusCode().value();
        if (statusCode == 429) {
            // 速率限制，设置 60 秒后重试
            accountService.setRateLimited(account.getId(), LocalDateTime.now().plusSeconds(60));
        } else if (statusCode == 529) {
            // API 过载
            accountService.setOverload(account.getId(), LocalDateTime.now().plusMinutes(5));
        } else if (statusCode == 401 || statusCode == 403) {
            // 认证失败
            accountService.setError(account.getId(), "认证失败: " + e.getMessage());
        }
    }

    /**
     * 记录用量
     */
    private void recordUsage(ProxyRequest request, Account account, Map<String, Object> response, long startTime) {
        try {
            // 解析响应中的 token 用量
            Long inputTokens = extractInputTokens(response);
            Long outputTokens = extractOutputTokens(response);

            UsageLog usageLog = new UsageLog();
            usageLog.setUserId(request.getUserId());
            usageLog.setApiKeyId(request.getApiKeyId());
            usageLog.setAccountId(account.getId());
            usageLog.setGroupId(request.getGroupId());
            usageLog.setPlatform(request.getPlatform());
            usageLog.setModel(request.getModel());
            usageLog.setInputTokens(inputTokens != null ? inputTokens.intValue() : 0);
            usageLog.setOutputTokens(outputTokens != null ? outputTokens.intValue() : 0);
            usageLog.setRateMultiplier(BigDecimal.ONE);
            usageLog.setStream(request.isStream());
            usageLog.setDurationMs((int) (System.currentTimeMillis() - startTime));
            usageLog.setIpAddress(request.getClientIp());
            usageLog.setCreatedAt(LocalDateTime.now());

            usageLogService.recordUsage(usageLog);

            // 更新 API Key 使用量
            if (request.getApiKeyId() != null) {
                Long cost = billingCalculator.calculateCost(
                        inputTokens != null ? inputTokens : 0L,
                        outputTokens != null ? outputTokens : 0L,
                        BigDecimal.ONE
                );
                apiKeyService.addQuotaUsed(request.getApiKeyId(), BigDecimal.valueOf(cost));
            }
        } catch (Exception e) {
            log.error("记录用量失败: {}", e.getMessage());
        }
    }

    /**
     * 从响应中提取输入 tokens
     */
    private Long extractInputTokens(Map<String, Object> response) {
        try {
            if (response.containsKey("usage")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> usage = (Map<String, Object>) response.get("usage");
                if (usage != null) {
                    Object promptTokens = usage.get("prompt_tokens");
                    if (promptTokens != null) {
                        return ((Number) promptTokens).longValue();
                    }
                    Object inputTokens = usage.get("input_tokens");
                    if (inputTokens != null) {
                        return ((Number) inputTokens).longValue();
                    }
                }
            }
        } catch (Exception e) {
            log.warn("提取 input tokens 失败: {}", e.getMessage());
        }
        return 0L;
    }

    /**
     * 从响应中提取输出 tokens
     */
    private Long extractOutputTokens(Map<String, Object> response) {
        try {
            if (response.containsKey("usage")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> usage = (Map<String, Object>) response.get("usage");
                if (usage != null) {
                    Object completionTokens = usage.get("completion_tokens");
                    if (completionTokens != null) {
                        return ((Number) completionTokens).longValue();
                    }
                    Object outputTokens = usage.get("output_tokens");
                    if (outputTokens != null) {
                        return ((Number) outputTokens).longValue();
                    }
                }
            }
        } catch (Exception e) {
            log.warn("提取 output tokens 失败: {}", e.getMessage());
        }
        return 0L;
    }

    /**
     * 获取可用的模型列表
     * 从分组下所有可调度账号的 model_mapping 聚合
     */
    public List<String> getAvailableModels(Long groupId, String platform) {
        // 获取可调度账号
        List<Account> accounts;
        if (groupId != null) {
            accounts = accountSelector.getAvailableAccountsByGroup(groupId);
        } else {
            accounts = accountSelector.getAvailableAccountsByPlatform(platform);
        }

        if (accounts == null || accounts.isEmpty()) {
            return java.util.Collections.emptyList();
        }

        // 收集所有 model_mapping 中的模型
        java.util.Set<String> modelSet = new java.util.TreeSet<>();
        boolean hasAnyMapping = false;

        for (Account account : accounts) {
            java.util.Map<String, String> mapping = accountService.getModelMapping(account);
            if (!mapping.isEmpty()) {
                hasAnyMapping = true;
                modelSet.addAll(mapping.keySet());
            }
        }

        // 如果没有任何 mapping，返回空列表（使用默认模型）
        if (!hasAnyMapping) {
            return java.util.Collections.emptyList();
        }

        return new java.util.ArrayList<>(modelSet);
    }

    /**
     * 代理请求参数
     */
    @lombok.Data
    public static class ProxyRequest {
        private Long userId;
        private Long apiKeyId;
        private Long groupId;
        private String platform;
        private String model;
        private String path;
        private Map<String, Object> body;
        private String sessionId;
        private Long inputTokens;
        private Integer timeout;
        private String clientIp;
        private boolean stream;
    }
}
