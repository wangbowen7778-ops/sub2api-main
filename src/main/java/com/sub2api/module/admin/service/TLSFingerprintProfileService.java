package com.sub2api.module.admin.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sub2api.module.admin.mapper.TLSFingerprintProfileMapper;
import com.sub2api.module.admin.model.entity.TLSFingerprintProfile;
import com.sub2api.module.common.exception.BusinessException;
import com.sub2api.module.common.model.enums.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * TLS 指纹配置服务
 *
 * @author Alibaba Java Code Guidelines
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TLSFingerprintProfileService extends ServiceImpl<TLSFingerprintProfileMapper, TLSFingerprintProfile> {

    private final TLSFingerprintProfileMapper mapper;
    private final StringRedisTemplate redisTemplate;

    // 本地缓存
    private final Map<Long, TLSFingerprintProfile> localCache = new ConcurrentHashMap<>();

    // 缓存 key
    private static final String CACHE_KEY = "tls_fingerprint_profiles";
    private static final String CACHE_KEY_ALL = "tls_fingerprint_profiles:all";

    /**
     * 获取所有配置
     */
    public List<TLSFingerprintProfile> listAll() {
        // 先尝试从本地缓存获取
        List<TLSFingerprintProfile> cached = getFromLocalCache();
        if (cached != null && !cached.isEmpty()) {
            return cached;
        }

        // 从数据库获取
        List<TLSFingerprintProfile> profiles = mapper.selectAllOrderByPriority();
        updateLocalCache(profiles);
        return profiles;
    }

    /**
     * 获取启用的配置
     */
    public List<TLSFingerprintProfile> listEnabled() {
        return mapper.selectEnabled();
    }

    /**
     * 获取配置
     */
    public TLSFingerprintProfile getById(Long id) {
        TLSFingerprintProfile profile = mapper.selectById(id);
        if (profile == null || profile.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "TLS 指纹配置不存在");
        }
        return profile;
    }

    /**
     * 创建配置
     */
    @Transactional(rollbackFor = Exception.class)
    public TLSFingerprintProfile create(TLSFingerprintProfile profile) {
        if (profile.getEnabled() == null) {
            profile.setEnabled(true);
        }
        if (profile.getPriority() == null) {
            profile.setPriority(50);
        }
        profile.setCreatedAt(OffsetDateTime.now());
        profile.setUpdatedAt(OffsetDateTime.now());

        mapper.insert(profile);
        invalidateCache();

        log.info("创建 TLS 指纹配置: id={}, name={}", profile.getId(), profile.getName());
        return profile;
    }

    /**
     * 更新配置
     */
    @Transactional(rollbackFor = Exception.class)
    public TLSFingerprintProfile update(Long id, TLSFingerprintProfile update) {
        TLSFingerprintProfile existing = getById(id);

        existing.setName(update.getName());
        existing.setFingerprintType(update.getFingerprintType());
        existing.setJa3(update.getJa3());
        existing.setHttp2Fingerprint(update.getHttp2Fingerprint());
        existing.setTlsVersion(update.getTlsVersion());
        existing.setCipherSuites(update.getCipherSuites());
        existing.setExtensions(update.getExtensions());
        existing.setEnabled(update.getEnabled());
        existing.setNotes(update.getNotes());
        existing.setPriority(update.getPriority());
        existing.setUpdatedAt(OffsetDateTime.now());

        mapper.updateById(existing);
        invalidateCache();

        log.info("更新 TLS 指纹配置: id={}", id);
        return existing;
    }

    /**
     * 删除配置
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        TLSFingerprintProfile existing = getById(id);
        existing.setDeletedAt(OffsetDateTime.now());
        existing.setUpdatedAt(OffsetDateTime.now());
        mapper.updateById(existing);
        invalidateCache();

        log.info("删除 TLS 指纹配置: id={}", id);
    }

    /**
     * 根据 ID 获取指纹配置（用于热路径）
     */
    public TLSFingerprintProfile getForHotPath(Long id) {
        TLSFingerprintProfile cached = localCache.get(id);
        if (cached != null) {
            return cached;
        }

        TLSFingerprintProfile profile = getById(id);
        localCache.put(id, profile);
        return profile;
    }

    /**
     * 随机获取一个启用的配置
     */
    public TLSFingerprintProfile getRandomEnabled() {
        List<TLSFingerprintProfile> enabled = listEnabled();
        if (enabled.isEmpty()) {
            return null;
        }
        int index = (int) (Math.random() * enabled.size());
        return enabled.get(index);
    }

    /**
     * 使缓存失效
     */
    public void invalidateCache() {
        localCache.clear();
        redisTemplate.delete(CACHE_KEY);
        redisTemplate.delete(CACHE_KEY_ALL);
        log.debug("TLS 指纹配置缓存已失效");
    }

    /**
     * 从本地缓存获取
     */
    private List<TLSFingerprintProfile> getFromLocalCache() {
        try {
            String cached = redisTemplate.opsForValue().get(CACHE_KEY_ALL);
            if (cached != null) {
                // 简单反序列化，实际应该用 JSON
                return null; // 简化处理
            }
        } catch (Exception e) {
            log.warn("获取 TLS 指纹配置缓存失败: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 更新本地缓存
     */
    private void updateLocalCache(List<TLSFingerprintProfile> profiles) {
        localCache.clear();
        for (TLSFingerprintProfile profile : profiles) {
            localCache.put(profile.getId(), profile);
        }
    }

    /**
     * 重新加载缓存
     */
    public void reloadCache() {
        invalidateCache();
        listAll();
        log.info("TLS 指纹配置缓存已重新加载");
    }
}
