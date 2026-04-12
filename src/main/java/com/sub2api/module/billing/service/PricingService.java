package com.sub2api.module.billing.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 动态定价服务
 * 从 LiteLLM 获取模型定价，支持本地缓存、哈希校验、fallback 定价
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PricingService {

    private final RestTemplate restTemplate;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${pricing.remote-url:}")
    private String remoteUrl;

    @Value("${pricing.hash-url:}")
    private String hashUrl;

    @Value("${pricing.data-dir:./data}")
    private String dataDir;

    @Value("${pricing.fallback-file:}")
    private String fallbackFile;

    @Value("${pricing.update-interval-hours:24}")
    private int updateIntervalHours;

    @Value("${pricing.hash-check-interval-minutes:10}")
    private int hashCheckIntervalMinutes;

    // 内存缓存
    private final Map<String, ModelPricing> pricingData = new ConcurrentHashMap<>();
    private final AtomicReference<LocalDateTime> lastUpdated = new AtomicReference<>();
    private final AtomicReference<String> localHash = new AtomicReference<>("");

    private volatile boolean running = true;

    // 默认 fallback 定价 (OpenAI GPT-4o)
    private static final ModelPricing DEFAULT_FALLBACK_PRICING = new ModelPricing();

    static {
        DEFAULT_FALLBACK_PRICING.setInputCostPerToken(2.5e-06);  // $2.5/M
        DEFAULT_FALLBACK_PRICING.setOutputCostPerToken(1.0e-05); // $10/M
        DEFAULT_FALLBACK_PRICING.setCacheReadInputTokenCost(1.25e-06); // $1.25/M
        DEFAULT_FALLBACK_PRICING.setSupportsPromptCaching(true);
    }

    /**
     * 模型定价
     */
    @Data
    public static class ModelPricing {
        private double inputCostPerToken = 0;
        private double inputCostPerTokenPriority = 0;
        private double outputCostPerToken = 0;
        private double outputCostPerTokenPriority = 0;
        private double cacheCreationInputTokenCost = 0;
        private double cacheCreationInputTokenCostAbove1hr = 0;
        private double cacheReadInputTokenCost = 0;
        private double cacheReadInputTokenCostPriority = 0;
        private int longContextInputTokenThreshold = 0;
        private double longContextInputCostMultiplier = 1.0;
        private double longContextOutputCostMultiplier = 1.0;
        private boolean supportsServiceTier = false;
        private String litellmProvider = "";
        private String mode = "";
        private boolean supportsPromptCaching = false;
        private double outputCostPerImage = 0;
        private double outputCostPerImageToken = 0;
    }

    /**
     * 初始化定价服务
     */
    @PostConstruct
    public void init() {
        try {
            // 确保数据目录存在
            Path dataPath = Paths.get(dataDir);
            if (!Files.exists(dataPath)) {
                Files.createDirectories(dataPath);
            }

            // 首次加载
            if (!checkAndUpdatePricing()) {
                // 下载失败，使用 fallback
                useFallbackPricing();
            }

            // 启动定时更新
            startUpdateScheduler();

            log.info("PricingService initialized with {} models", pricingData.size());
        } catch (Exception e) {
            log.error("Failed to initialize PricingService: {}", e.getMessage());
            useFallbackPricing();
        }
    }

    /**
     * 停止服务
     */
    @PreDestroy
    public void stop() {
        running = false;
        log.info("PricingService stopped");
    }

    /**
     * 启动定时更新调度器
     */
    private void startUpdateScheduler() {
        // 定期检查哈希更新
        long hashCheckIntervalMs = Math.max(hashCheckIntervalMinutes, 1) * 60 * 1000L;

        Thread updateThread = new Thread(() -> {
            while (running) {
                try {
                    Thread.sleep(hashCheckIntervalMs);
                    if (running) {
                        syncWithRemote();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "pricing-update-scheduler");
        updateThread.setDaemon(true);
        updateThread.start();

        log.info("Pricing update scheduler started (check every {} minutes)", hashCheckIntervalMinutes);
    }

    /**
     * 检查并更新定价数据
     */
    private boolean checkAndUpdatePricing() {
        Path pricingFile = Paths.get(dataDir, "model_pricing.json");

        // 检查本地文件是否存在
        if (!Files.exists(pricingFile)) {
            log.info("Local pricing file not found, downloading...");
            return downloadPricingData();
        }

        // 加载本地文件
        try {
            loadPricingData(pricingFile);
        } catch (Exception e) {
            log.warn("Failed to load local pricing file: {}", e.getMessage());
            return downloadPricingData();
        }

        // 如果配置了哈希URL，通过远程哈希检查是否有更新
        if (hashUrl != null && !hashUrl.isBlank()) {
            try {
                String remoteHash = fetchRemoteHash();
                String currentHash = localHash.get();

                if (currentHash.isEmpty() || !currentHash.equals(remoteHash)) {
                    log.info("Remote hash differs (local={}, remote={}), downloading...",
                            truncateHash(currentHash), truncateHash(remoteHash));
                    return downloadPricingData();
                }
            } catch (Exception e) {
                log.warn("Failed to fetch remote hash: {}", e.getMessage());
                // 继续使用本地文件
            }
        }

        return true;
    }

    /**
     * 与远程同步
     */
    private void syncWithRemote() {
        if (hashUrl == null || hashUrl.isBlank()) {
            // 基于时间检查
            Path pricingFile = Paths.get(dataDir, "model_pricing.json");
            try {
                if (Files.exists(pricingFile)) {
                    LocalDateTime modTime = LocalDateTime.ofInstant(
                            Files.getLastModifiedTime(pricingFile).toInstant(),
                            java.time.ZoneId.systemDefault());
                    long hoursSinceUpdate = Duration.between(modTime, LocalDateTime.now()).toHours();

                    if (hoursSinceUpdate >= updateIntervalHours) {
                        log.info("Local file is {} hours old, updating...", hoursSinceUpdate);
                        downloadPricingData();
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to check file age: {}", e.getMessage());
            }
            return;
        }

        // 基于哈希检查
        try {
            String remoteHash = fetchRemoteHash();
            String currentHash = localHash.get();

            if (!currentHash.isEmpty() && currentHash.equals(remoteHash)) {
                log.debug("Hash check passed, no update needed");
                return;
            }

            log.info("Remote hash differs, downloading new version...");
            downloadPricingData();
        } catch (Exception e) {
            log.warn("Failed to sync with remote: {}", e.getMessage());
        }
    }

    /**
     * 从远程下载定价数据
     */
    private boolean downloadPricingData() {
        if (remoteUrl == null || remoteUrl.isBlank()) {
            log.warn("No remote URL configured, using fallback pricing");
            return false;
        }

        try {
            log.info("Downloading pricing data from {}", remoteUrl);

            // 获取远程哈希（用于同步锚点）
            String remoteHash = null;
            if (hashUrl != null && !hashUrl.isBlank()) {
                try {
                    remoteHash = fetchRemoteHash();
                } catch (Exception e) {
                    log.warn("Failed to fetch remote hash: {}", e.getMessage());
                }
            }

            // 下载数据
            String jsonStr = restTemplate.getForObject(remoteUrl, String.class);

            // 解析数据
            Map<String, ModelPricing> data = parsePricingData(jsonStr);

            // 保存到本地文件
            Path pricingFile = Paths.get(dataDir, "model_pricing.json");
            Files.writeString(pricingFile, jsonStr);

            // 保存哈希
            if (remoteHash != null) {
                Path hashFile = Paths.get(dataDir, "model_pricing.sha256");
                Files.writeString(hashFile, remoteHash + "\n");
                localHash.set(remoteHash);
            } else {
                // 使用数据本身的哈希
                String dataHash = sha256(jsonStr);
                localHash.set(dataHash);
            }

            // 更新内存数据
            pricingData.clear();
            pricingData.putAll(data);
            lastUpdated.set(LocalDateTime.now());

            log.info("Downloaded {} models successfully", data.size());
            return true;

        } catch (Exception e) {
            log.error("Failed to download pricing data: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 解析定价数据
     */
    private Map<String, ModelPricing> parsePricingData(String jsonStr) throws Exception {
        JsonNode root = objectMapper.readTree(jsonStr);
        Map<String, ModelPricing> result = new ConcurrentHashMap<>();

        root.fields().forEachRemaining(entry -> {
            String modelName = entry.getKey();
            if ("sample_spec".equals(modelName)) {
                return;
            }

            JsonNode node = entry.getValue();
            if (node == null || !node.isObject()) {
                return;
            }

            ModelPricing pricing = new ModelPricing();

            // 只保留有有效价格的条目
            if (!node.has("input_cost_per_token") && !node.has("output_cost_per_token")) {
                return;
            }

            pricing.setInputCostPerToken(getDouble(node, "input_cost_per_token"));
            pricing.setInputCostPerTokenPriority(getDouble(node, "input_cost_per_token_priority"));
            pricing.setOutputCostPerToken(getDouble(node, "output_cost_per_token"));
            pricing.setOutputCostPerTokenPriority(getDouble(node, "output_cost_per_token_priority"));
            pricing.setCacheCreationInputTokenCost(getDouble(node, "cache_creation_input_token_cost"));
            pricing.setCacheCreationInputTokenCostAbove1hr(getDouble(node, "cache_creation_input_token_cost_above_1hr"));
            pricing.setCacheReadInputTokenCost(getDouble(node, "cache_read_input_token_cost"));
            pricing.setCacheReadInputTokenCostPriority(getDouble(node, "cache_read_input_token_cost_priority"));
            pricing.setLongContextInputTokenThreshold(getInt(node, "long_context_input_token_threshold"));
            pricing.setLongContextInputCostMultiplier(getDoubleOr(node, "long_context_input_cost_multiplier", 1.0));
            pricing.setLongContextOutputCostMultiplier(getDoubleOr(node, "long_context_output_cost_multiplier", 1.0));
            pricing.setSupportsServiceTier(node.has("supports_service_tier") && node.get("supports_service_tier").asBoolean());
            pricing.setLitellmProvider(getString(node, "litellm_provider"));
            pricing.setMode(getString(node, "mode"));
            pricing.setSupportsPromptCaching(node.has("supports_prompt_caching") && node.get("supports_prompt_caching").asBoolean());
            pricing.setOutputCostPerImage(getDouble(node, "output_cost_per_image"));
            pricing.setOutputCostPerImageToken(getDouble(node, "output_cost_per_image_token"));

            result.put(modelName, pricing);
        });

        if (result.isEmpty()) {
            throw new RuntimeException("No valid pricing entries found");
        }

        return result;
    }

    /**
     * 从本地文件加载定价数据
     */
    private void loadPricingData(Path filePath) throws Exception {
        String jsonStr = Files.readString(filePath);
        Map<String, ModelPricing> data = parsePricingData(jsonStr);

        // 计算哈希
        String dataHash = sha256(jsonStr);

        pricingData.clear();
        pricingData.putAll(data);
        localHash.set(dataHash);
        lastUpdated.set(LocalDateTime.now());

        log.info("Loaded {} models from {}", data.size(), filePath);
    }

    /**
     * 使用 fallback 定价文件
     */
    private void useFallbackPricing() {
        if (fallbackFile == null || fallbackFile.isBlank()) {
            log.warn("No fallback file configured, using hardcoded defaults");
            pricingData.clear();
            // 添加一些默认定价
            addDefaultPricing();
            return;
        }

        try {
            Path fallbackPath = Paths.get(fallbackFile);
            if (!Files.exists(fallbackPath)) {
                log.warn("Fallback file not found: {}", fallbackFile);
                addDefaultPricing();
                return;
            }

            loadPricingData(fallbackPath);
            log.info("Loaded fallback pricing from {}", fallbackFile);
        } catch (Exception e) {
            log.error("Failed to load fallback pricing: {}", e.getMessage());
            addDefaultPricing();
        }
    }

    /**
     * 添加默认定价
     */
    private void addDefaultPricing() {
        // OpenAI GPT-4o
        ModelPricing gpt4o = new ModelPricing();
        gpt4o.setInputCostPerToken(2.5e-06);
        gpt4o.setOutputCostPerToken(1.0e-05);
        gpt4o.setCacheReadInputTokenCost(1.25e-06);
        gpt4o.setSupportsPromptCaching(true);
        pricingData.put("gpt-4o", gpt4o);

        // Anthropic Claude
        ModelPricing claude = new ModelPricing();
        claude.setInputCostPerToken(3.0e-06);
        claude.setOutputCostPerToken(1.5e-05);
        claude.setCacheCreationInputTokenCost(3.75e-07);
        claude.setCacheReadInputTokenCost(3.75e-07);
        claude.setSupportsPromptCaching(true);
        pricingData.put("claude-3-5-sonnet-20241022", claude);

        lastUpdated.set(LocalDateTime.now());
    }

    /**
     * 从远程获取哈希值
     */
    private String fetchRemoteHash() throws Exception {
        if (hashUrl == null || hashUrl.isBlank()) {
            return "";
        }
        String hash = restTemplate.getForObject(hashUrl, String.class);
        return hash != null ? hash.trim() : "";
    }

    /**
     * 获取模型定价（带模糊匹配）
     */
    public ModelPricing getModelPricing(String modelName) {
        if (modelName == null || modelName.isBlank()) {
            return null;
        }

        String normalized = normalizeModelName(modelName.toLowerCase().trim());

        // 1. 精确匹配
        ModelPricing pricing = pricingData.get(normalized);
        if (pricing != null) {
            return pricing;
        }

        // 2. 尝试去掉版本号后缀
        String baseName = extractBaseName(normalized);
        for (Map.Entry<String, ModelPricing> entry : pricingData.entrySet()) {
            String key = entry.getKey().toLowerCase();
            if (key.equals(baseName) || key.startsWith(baseName + "-")) {
                return entry.getValue();
            }
        }

        // 3. OpenAI 模型回退策略
        if (normalized.startsWith("gpt-")) {
            return matchOpenAIModel(normalized);
        }

        // 4. Claude 模型系列匹配
        return matchClaudeModel(normalized);
    }

    /**
     * 标准化模型名称
     */
    private String normalizeModelName(String model) {
        // 去掉常见前缀
        model = model.replace("models/", "");
        model = model.replace("publishers/google/models/", "");

        // 处理 Gemini/VertexAI 格式
        int idx = model.lastIndexOf("/models/");
        if (idx > 0) {
            model = model.substring(idx + 8);
        }

        return model.trim();
    }

    /**
     * 提取基础模型名称（去掉日期版本号）
     */
    private String extractBaseName(String model) {
        // 移除日期后缀 (如 -20251101, -20241022)
        return model.replaceAll("-\\d{8}$", "")
                    .replaceAll("-v\\d+:\\d+$", "");
    }

    /**
     * OpenAI 模型回退匹配策略
     */
    private ModelPricing matchOpenAIModel(String model) {
        // gpt-4o -> 使用 gpt-4o 定价
        if (model.contains("gpt-4o")) {
            ModelPricing p = pricingData.get("gpt-4o");
            if (p != null) return p;
        }

        // gpt-4-turbo -> 使用 gpt-4o 定价
        if (model.contains("gpt-4-turbo")) {
            ModelPricing p = pricingData.get("gpt-4o");
            if (p != null) return p;
        }

        // gpt-3.5-turbo -> 默认
        if (model.contains("gpt-3.5-turbo")) {
            ModelPricing p = pricingData.get("gpt-3.5-turbo");
            if (p != null) return p;
        }

        return DEFAULT_FALLBACK_PRICING;
    }

    /**
     * Claude 模型系列匹配
     */
    private ModelPricing matchClaudeModel(String model) {
        // claude-3.5-sonnet
        if (model.contains("claude-3.5-sonnet") || model.contains("claude-3-5-sonnet")) {
            ModelPricing p = pricingData.get("claude-3.5-sonnet-20241022");
            if (p != null) return p;
        }

        // claude-3-opus
        if (model.contains("claude-3-opus")) {
            ModelPricing p = pricingData.get("claude-3-opus-20240229");
            if (p != null) return p;
        }

        // claude-3-haiku
        if (model.contains("claude-3-haiku")) {
            ModelPricing p = pricingData.get("claude-3-haiku-20240307");
            if (p != null) return p;
        }

        return null;
    }

    /**
     * 获取服务状态
     */
    public Map<String, Object> getStatus() {
        return Map.of(
            "model_count", pricingData.size(),
            "last_updated", lastUpdated.get() != null ? lastUpdated.get().toString() : "never",
            "local_hash", truncateHash(localHash.get())
        );
    }

    /**
     * 强制更新
     */
    public boolean forceUpdate() {
        return downloadPricingData();
    }

    // ========== 辅助方法 ==========

    private double getDouble(JsonNode node, String field) {
        JsonNode n = node.get(field);
        return n != null && !n.isNull() ? n.asDouble() : 0;
    }

    private double getDoubleOr(JsonNode node, String field, double defaultValue) {
        JsonNode n = node.get(field);
        return n != null && !n.isNull() ? n.asDouble() : defaultValue;
    }

    private int getInt(JsonNode node, String field) {
        JsonNode n = node.get(field);
        return n != null && !n.isNull() ? n.asInt() : 0;
    }

    private String getString(JsonNode node, String field) {
        JsonNode n = node.get(field);
        return n != null && !n.isNull() ? n.asText() : "";
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private String truncateHash(String hash) {
        if (hash == null || hash.isEmpty()) return "(empty)";
        return hash.length() > 8 ? hash.substring(0, 8) : hash;
    }
}
