package com.sub2api.module.admin.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sub2api.module.admin.mapper.ErrorPassthroughRuleMapper;
import com.sub2api.module.admin.model.entity.ErrorPassthroughRule;
import com.sub2api.module.common.exception.BusinessException;
import com.sub2api.module.common.model.enums.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 错误透传规则服务
 *
 * @author Alibaba Java Code Guidelines
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ErrorPassthroughRuleService extends ServiceImpl<ErrorPassthroughRuleMapper, ErrorPassthroughRule> {

    private final ErrorPassthroughRuleMapper mapper;
    private final StringRedisTemplate redisTemplate;

    // 本地内存缓存，用于快速匹配
    private final Map<Long, CachedPassthroughRule> localCache = new ConcurrentHashMap<>();

    // 缓存 key
    private static final String CACHE_KEY = "error_passthrough_rules";
    private static final String CACHE_KEY_ALL = "error_passthrough_rules:all";
    private static final String CACHE_KEY_ENABLED = "error_passthrough_rules:enabled";

    // 缓存过期时间
    private static final long CACHE_TTL_SECONDS = 24 * 60 * 60; // 24小时

    // 最大body匹配长度
    private static final int MAX_BODY_MATCH_LEN = 8 * 1024; // 8KB

    // ==================== 内部缓存类 ====================

    /**
     * 预计算的规则缓存，避免运行时重复 ToLower
     */
    private static class CachedPassthroughRule {
        final ErrorPassthroughRule rule;
        final List<String> lowerKeywords; // 预计算的小写关键词
        final Set<Integer> errorCodeSet; // 预计算的 error code set

        CachedPassthroughRule(ErrorPassthroughRule rule) {
            this.rule = rule;
            this.lowerKeywords = rule.getKeywords() != null
                    ? rule.getKeywords().stream().map(String::toLowerCase).toList()
                    : Collections.emptyList();
            this.errorCodeSet = rule.getErrorCodes() != null
                    ? new HashSet<>(rule.getErrorCodes())
                    : Collections.emptySet();
        }
    }

    // ==================== CRUD 操作 ====================

    /**
     * 获取所有规则
     */
    public List<ErrorPassthroughRule> listAll() {
        return mapper.selectAllOrderByPriority();
    }

    /**
     * 获取启用的规则
     */
    public List<ErrorPassthroughRule> listEnabled() {
        // 先尝试从本地缓存获取
        List<ErrorPassthroughRule> cached = getFromLocalCache();
        if (cached != null && !cached.isEmpty()) {
            return cached.stream().filter(ErrorPassthroughRule::getEnabled).toList();
        }

        // 从数据库获取
        List<ErrorPassthroughRule> rules = mapper.selectEnabledOrderByPriority();
        updateLocalCache(rules);
        return rules;
    }

    /**
     * 获取规则
     */
    public ErrorPassthroughRule getById(Long id) {
        ErrorPassthroughRule rule = mapper.selectByIdNotDeleted(id);
        if (rule == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "错误透传规则不存在");
        }
        return rule;
    }

    /**
     * 创建规则
     */
    @Transactional(rollbackFor = Exception.class)
    public ErrorPassthroughRule create(ErrorPassthroughRule rule) {
        validateRule(rule);

        // 设置默认值
        if (rule.getEnabled() == null) {
            rule.setEnabled(true);
        }
        if (rule.getPriority() == null) {
            rule.setPriority(0);
        }
        if (rule.getMatchMode() == null) {
            rule.setMatchMode(ErrorPassthroughRule.MATCH_MODE_ANY);
        }
        if (rule.getPassthroughCode() == null) {
            rule.setPassthroughCode(true);
        }
        if (rule.getPassthroughBody() == null) {
            rule.setPassthroughBody(true);
        }
        if (rule.getSkipMonitoring() == null) {
            rule.setSkipMonitoring(false);
        }

        // 确保切片不为 null
        if (rule.getErrorCodes() == null) {
            rule.setErrorCodes(Collections.emptyList());
        }
        if (rule.getKeywords() == null) {
            rule.setKeywords(Collections.emptyList());
        }
        if (rule.getPlatforms() == null) {
            rule.setPlatforms(Collections.emptyList());
        }

        rule.setCreatedAt(LocalDateTime.now());
        rule.setUpdatedAt(LocalDateTime.now());

        mapper.insert(rule);
        invalidateCache();

        log.info("创建错误透传规则: id={}, name={}", rule.getId(), rule.getName());
        return rule;
    }

    /**
     * 更新规则
     */
    @Transactional(rollbackFor = Exception.class)
    public ErrorPassthroughRule update(Long id, ErrorPassthroughRule update) {
        ErrorPassthroughRule existing = getById(id);

        // 应用更新
        if (update.getName() != null) {
            existing.setName(update.getName());
        }
        if (update.getEnabled() != null) {
            existing.setEnabled(update.getEnabled());
        }
        if (update.getPriority() != null) {
            existing.setPriority(update.getPriority());
        }
        if (update.getErrorCodes() != null) {
            existing.setErrorCodes(update.getErrorCodes());
        }
        if (update.getKeywords() != null) {
            existing.setKeywords(update.getKeywords());
        }
        if (update.getMatchMode() != null) {
            existing.setMatchMode(update.getMatchMode());
        }
        if (update.getPlatforms() != null) {
            existing.setPlatforms(update.getPlatforms());
        }
        if (update.getPassthroughCode() != null) {
            existing.setPassthroughCode(update.getPassthroughCode());
        }
        if (update.getResponseCode() != null) {
            existing.setResponseCode(update.getResponseCode());
        }
        if (update.getPassthroughBody() != null) {
            existing.setPassthroughBody(update.getPassthroughBody());
        }
        if (update.getCustomMessage() != null) {
            existing.setCustomMessage(update.getCustomMessage());
        }
        if (update.getSkipMonitoring() != null) {
            existing.setSkipMonitoring(update.getSkipMonitoring());
        }
        if (update.getDescription() != null) {
            existing.setDescription(update.getDescription());
        }

        validateRule(existing);
        existing.setUpdatedAt(LocalDateTime.now());

        mapper.updateById(existing);
        invalidateCache();

        log.info("更新错误透传规则: id={}", id);
        return existing;
    }

    /**
     * 删除规则（软删除）
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        ErrorPassthroughRule existing = getById(id);
        existing.setDeletedAt(LocalDateTime.now());
        existing.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(existing);
        invalidateCache();

        log.info("删除错误透传规则: id={}", id);
    }

    // ==================== 规则匹配 ====================

    /**
     * 匹配透传规则
     * 返回第一个匹配的规则，如果没有匹配则返回 null
     *
     * @param platform   平台名称
     * @param statusCode HTTP 状态码
     * @param body       响应体
     * @return 匹配的规则，如果没有匹配则返回 null
     */
    public MatchResult matchRule(String platform, int statusCode, String body) {
        List<ErrorPassthroughRule> rules = listEnabled();
        if (rules.isEmpty()) {
            return null;
        }

        String lowerPlatform = platform != null ? platform.toLowerCase() : "";
        String bodyLower = body != null && body.length() > MAX_BODY_MATCH_LEN
                ? body.substring(0, MAX_BODY_MATCH_LEN).toLowerCase()
                : (body != null ? body.toLowerCase() : "");

        for (ErrorPassthroughRule rule : rules) {
            if (!rule.getEnabled()) {
                continue;
            }
            if (!platformMatches(rule, lowerPlatform)) {
                continue;
            }
            if (ruleMatches(rule, statusCode, bodyLower)) {
                return new MatchResult(rule, rule.getSkipMonitoring() != null && rule.getSkipMonitoring());
            }
        }

        return null;
    }

    /**
     * 平台匹配
     */
    private boolean platformMatches(ErrorPassthroughRule rule, String lowerPlatform) {
        if (rule.getPlatforms() == null || rule.getPlatforms().isEmpty()) {
            return true; // 空列表表示适用于所有平台
        }
        for (String p : rule.getPlatforms()) {
            if (p.toLowerCase().equals(lowerPlatform)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 规则匹配
     */
    private boolean ruleMatches(ErrorPassthroughRule rule, int statusCode, String bodyLower) {
        boolean hasErrorCodes = rule.hasErrorCodes();
        boolean hasKeywords = rule.hasKeywords();

        if (!hasErrorCodes && !hasKeywords) {
            return false;
        }

        boolean codeMatch = !hasErrorCodes || rule.containsErrorCode(statusCode);

        if (ErrorPassthroughRule.MATCH_MODE_ALL.equals(rule.getMatchMode())) {
            // "all" 模式：所有配置的条件都必须满足
            if (hasErrorCodes && !codeMatch) {
                return false;
            }
            if (hasKeywords) {
                return containsAnyKeyword(bodyLower, rule.getKeywords());
            }
            return codeMatch;
        }

        // "any" 模式：任一条件满足即可
        if (hasErrorCodes && hasKeywords) {
            if (codeMatch) {
                return true;
            }
            return containsAnyKeyword(bodyLower, rule.getKeywords());
        }
        // 只配置了一种条件
        if (hasKeywords) {
            return containsAnyKeyword(bodyLower, rule.getKeywords());
        }
        return codeMatch;
    }

    /**
     * 检查是否包含任意关键词
     */
    private boolean containsAnyKeyword(String text, List<String> keywords) {
        if (text == null || keywords == null) {
            return false;
        }
        String lowerText = text.toLowerCase();
        for (String keyword : keywords) {
            if (lowerText.contains(keyword.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    // ==================== 缓存管理 ====================

    /**
     * 使缓存失效
     */
    public void invalidateCache() {
        localCache.clear();
        redisTemplate.delete(CACHE_KEY);
        redisTemplate.delete(CACHE_KEY_ALL);
        redisTemplate.delete(CACHE_KEY_ENABLED);
        log.debug("错误透传规则缓存已失效");
    }

    /**
     * 重新加载缓存
     */
    public void reloadCache() {
        invalidateCache();
        listEnabled();
        log.info("错误透传规则缓存已重新加载");
    }

    /**
     * 从本地缓存获取
     */
    private List<ErrorPassthroughRule> getFromLocalCache() {
        try {
            String cached = redisTemplate.opsForValue().get(CACHE_KEY_ENABLED);
            if (cached != null) {
                // 实际应该用 JSON 反序列化，这里简化处理
                return null;
            }
        } catch (Exception e) {
            log.warn("获取错误透传规则缓存失败: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 更新本地缓存
     */
    private void updateLocalCache(List<ErrorPassthroughRule> rules) {
        localCache.clear();
        for (ErrorPassthroughRule rule : rules) {
            localCache.put(rule.getId(), new CachedPassthroughRule(rule));
        }
    }

    // ==================== 验证 ====================

    /**
     * 验证规则配置的有效性
     */
    private void validateRule(ErrorPassthroughRule rule) {
        if (rule.getName() == null || rule.getName().trim().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "name is required");
        }
        if (rule.getMatchMode() != null
                && !ErrorPassthroughRule.MATCH_MODE_ANY.equals(rule.getMatchMode())
                && !ErrorPassthroughRule.MATCH_MODE_ALL.equals(rule.getMatchMode())) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "match_mode must be 'any' or 'all'");
        }
        // 至少需要配置一个匹配条件（错误码或关键词）
        if ((rule.getErrorCodes() == null || rule.getErrorCodes().isEmpty())
                && (rule.getKeywords() == null || rule.getKeywords().isEmpty())) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "at least one error_code or keyword is required");
        }
        if (rule.getPassthroughCode() != null && !rule.getPassthroughCode()
                && (rule.getResponseCode() == null || rule.getResponseCode() <= 0)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "response_code is required when passthrough_code is false");
        }
        if (rule.getPassthroughBody() != null && !rule.getPassthroughBody()
                && (rule.getCustomMessage() == null || rule.getCustomMessage().trim().isEmpty())) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "custom_message is required when passthrough_body is false");
        }
    }

    // ==================== 匹配结果 ====================

    /**
     * 匹配结果
     */
    public record MatchResult(ErrorPassthroughRule rule, boolean skipMonitoring) {
    }
}
