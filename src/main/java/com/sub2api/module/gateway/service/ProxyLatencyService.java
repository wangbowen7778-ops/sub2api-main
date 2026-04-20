package com.sub2api.module.gateway.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 代理延迟追踪服务
 * 追踪代理响应延迟，缓存结果用于负载均衡决策
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProxyLatencyService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ProxyLatencyService.class);

    private final StringRedisTemplate redisTemplate;

    private static final String LATENCY_KEY_PREFIX = "proxy:latency:";
    private static final Duration LATENCY_TTL = Duration.ofMinutes(5);

    /**
     * 记录代理延迟
     */
    public void recordLatency(Long proxyId, boolean success, long latencyMs, String ipAddress) {
        String key = LATENCY_KEY_PREFIX + proxyId;
        try {
            ProxyLatencyInfo info = new ProxyLatencyInfo();
            info.setSuccess(success);
            info.setLatencyMs(latencyMs);
            info.setIpAddress(ipAddress);
            info.setUpdatedAt(OffsetDateTime.now());

            redisTemplate.opsForValue().set(key, toJson(info), LATENCY_TTL);
        } catch (Exception e) {
            log.error("记录代理延迟失败: proxyId={}, error={}", proxyId, e.getMessage());
        }
    }

    /**
     * 获取代理延迟信息
     */
    public ProxyLatencyInfo getLatency(Long proxyId) {
        String key = LATENCY_KEY_PREFIX + proxyId;
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json != null) {
                return fromJson(json);
            }
        } catch (Exception e) {
            log.error("获取代理延迟失败: proxyId={}, error={}", proxyId, e.getMessage());
        }
        return null;
    }

    /**
     * 批量获取代理延迟
     */
    public Map<Long, ProxyLatencyInfo> getLatencies(List<Long> proxyIds) {
        Map<Long, ProxyLatencyInfo> result = new HashMap<>();
        if (proxyIds == null || proxyIds.isEmpty()) {
            return result;
        }

        for (Long proxyId : proxyIds) {
            ProxyLatencyInfo info = getLatency(proxyId);
            if (info != null) {
                result.put(proxyId, info);
            }
        }
        return result;
    }

    /**
     * 获取最优代理（延迟最低且成功的）
     */
    public Long selectBestProxy(List<Long> proxyIds) {
        Long bestProxyId = null;
        long bestLatency = Long.MAX_VALUE;

        for (Long proxyId : proxyIds) {
            ProxyLatencyInfo info = getLatency(proxyId);
            if (info != null && info.isSuccess() && info.getLatencyMs() < bestLatency) {
                bestLatency = info.getLatencyMs();
                bestProxyId = proxyId;
            }
        }

        return bestProxyId;
    }

    /**
     * 获取代理平均延迟
     */
    public long getAverageLatency(List<Long> proxyIds) {
        if (proxyIds == null || proxyIds.isEmpty()) {
            return 0;
        }

        long totalLatency = 0;
        int successCount = 0;

        for (Long proxyId : proxyIds) {
            ProxyLatencyInfo info = getLatency(proxyId);
            if (info != null && info.isSuccess()) {
                totalLatency += info.getLatencyMs();
                successCount++;
            }
        }

        return successCount > 0 ? totalLatency / successCount : 0;
    }

    /**
     * 判断代理是否健康（最近有成功记录）
     */
    public boolean isProxyHealthy(Long proxyId) {
        ProxyLatencyInfo info = getLatency(proxyId);
        return info != null && info.isSuccess();
    }

    // ========== 内部类 ==========

    public static class ProxyLatencyInfo implements java.io.Serializable {
        private boolean success;
        private Long latencyMs;
        private String message;
        private String ipAddress;
        private String country;
        private String countryCode;
        private String region;
        private String city;
        private String qualityStatus;
        private Integer qualityScore;
        private String qualityGrade;
        private OffsetDateTime updatedAt;

        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public Long getLatencyMs() { return latencyMs; }
        public void setLatencyMs(Long latencyMs) { this.latencyMs = latencyMs; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public String getIpAddress() { return ipAddress; }
        public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
        public String getCountry() { return country; }
        public void setCountry(String country) { this.country = country; }
        public String getCountryCode() { return countryCode; }
        public void setCountryCode(String countryCode) { this.countryCode = countryCode; }
        public String getRegion() { return region; }
        public void setRegion(String region) { this.region = region; }
        public String getCity() { return city; }
        public void setCity(String city) { this.city = city; }
        public String getQualityStatus() { return qualityStatus; }
        public void setQualityStatus(String qualityStatus) { this.qualityStatus = qualityStatus; }
        public Integer getQualityScore() { return qualityScore; }
        public void setQualityScore(Integer qualityScore) { this.qualityScore = qualityScore; }
        public String getQualityGrade() { return qualityGrade; }
        public void setQualityGrade(String qualityGrade) { this.qualityGrade = qualityGrade; }
        public OffsetDateTime getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
    }

    // ========== JSON 辅助 ==========

    private String toJson(ProxyLatencyInfo info) {
        return String.format(
            "{\"success\":%s,\"latencyMs\":%d,\"ipAddress\":\"%s\",\"updatedAt\":\"%s\"}",
            info.isSuccess(),
            info.getLatencyMs() != null ? info.getLatencyMs() : 0,
            info.getIpAddress() != null ? info.getIpAddress() : "",
            info.getUpdatedAt() != null ? info.getUpdatedAt().toString() : ""
        );
    }

    private ProxyLatencyInfo fromJson(String json) {
        ProxyLatencyInfo info = new ProxyLatencyInfo();
        try {
            // 简单解析 JSON
            if (json.contains("\"success\":true")) {
                info.setSuccess(true);
            } else if (json.contains("\"success\":false")) {
                info.setSuccess(false);
            }

            int latencyIdx = json.indexOf("\"latencyMs\":");
            if (latencyIdx > 0) {
                int start = latencyIdx + 11;
                int end = json.indexOf(",", start);
                if (end < 0) end = json.indexOf("}", start);
                String latency = json.substring(start, end).trim();
                info.setLatencyMs(Long.parseLong(latency));
            }
        } catch (Exception e) {
            log.warn("解析延迟信息失败: {}", e.getMessage());
        }
        return info;
    }
}
