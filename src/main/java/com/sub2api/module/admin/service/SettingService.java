package com.sub2api.module.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sub2api.module.admin.mapper.SettingMapper;
import com.sub2api.module.admin.model.entity.Setting;
import com.sub2api.module.common.exception.BusinessException;
import com.sub2api.module.common.model.enums.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 系统设置服务
 *
 * @author Alibaba Java Code Guidelines
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SettingService extends ServiceImpl<SettingMapper, Setting> {

    private final SettingMapper settingMapper;
    private final StringRedisTemplate redisTemplate;

    // 缓存 key 前缀
    private static final String CACHE_KEY_PREFIX = "setting:";
    // 缓存过期时间
    private static final long CACHE_TTL_SECONDS = 60;

    // 常用设置键
    public static final String KEY_REGISTRATION_ENABLED = "registration_enabled";
    public static final String KEY_DEFAULT_GROUP_ID = "default_group_id";
    public static final String KEY_DEFAULT_SUBSCRIPTION_GROUP_ID = "default_subscription_group_id";
    public static final String KEY_MAINTENANCE_MODE = "maintenance_mode";
    public static final String KEY_CLAUDE_CODE_VERSION_MIN = "claude_code_version_min";
    public static final String KEY_CLAUDE_CODE_VERSION_MAX = "claude_code_version_max";

    /**
     * 获取设置值
     */
    public String getValue(String key) {
        // 先从缓存获取
        String cacheKey = CACHE_KEY_PREFIX + key;
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return cached;
        }

        // 从数据库获取
        Setting setting = settingMapper.selectByKey(key);
        if (setting == null) {
            return null;
        }

        // 写入缓存
        redisTemplate.opsForValue().set(cacheKey, setting.getSettingValue(), CACHE_TTL_SECONDS, TimeUnit.SECONDS);

        return setting.getSettingValue();
    }

    /**
     * 获取设置值（带默认值）
     */
    public String getValue(String key, String defaultValue) {
        String value = getValue(key);
        return value != null ? value : defaultValue;
    }

    /**
     * 获取布尔值设置
     */
    public boolean getBoolean(String key, boolean defaultValue) {
        String value = getValue(key);
        if (value == null) {
            return defaultValue;
        }
        return "true".equalsIgnoreCase(value) || "1".equals(value);
    }

    /**
     * 获取整数值设置
     */
    public int getInt(String key, int defaultValue) {
        String value = getValue(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 获取长整数值设置
     */
    public long getLong(String key, long defaultValue) {
        String value = getValue(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 设置值
     */
    @Transactional(rollbackFor = Exception.class)
    public void setValue(String key, String value) {
        Setting setting = settingMapper.selectByKey(key);
        if (setting == null) {
            // 创建新设置
            setting = new Setting();
            setting.setSettingKey(key);
            setting.setSettingValue(value);
            setting.setSettingType(determineType(value));
            setting.setEditable(true);
            setting.setCreatedAt(java.time.LocalDateTime.now());
            setting.setUpdatedAt(java.time.LocalDateTime.now());
            settingMapper.insert(setting);
        } else {
            // 更新现有设置
            setting.setSettingValue(value);
            setting.setSettingType(determineType(value));
            setting.setUpdatedAt(java.time.LocalDateTime.now());
            settingMapper.updateById(setting);
        }

        // 清除缓存
        String cacheKey = CACHE_KEY_PREFIX + key;
        redisTemplate.delete(cacheKey);

        log.info("设置已更新: key={}, value={}", key, value);
    }

    /**
     * 批量设置值
     */
    @Transactional(rollbackFor = Exception.class)
    public void setValues(Map<String, String> settings) {
        for (Map.Entry<String, String> entry : settings.entrySet()) {
            setValue(entry.getKey(), entry.getValue());
        }
    }

    /**
     * 获取所有设置
     */
    public Map<String, String> getAllSettings() {
        List<Setting> settings = settingMapper.selectAll();
        Map<String, String> result = new HashMap<>();
        for (Setting setting : settings) {
            result.put(setting.getSettingKey(), setting.getSettingValue());
        }
        return result;
    }

    /**
     * 获取分类下的所有设置
     */
    public Map<String, String> getSettingsByCategory(String category) {
        List<Setting> settings = settingMapper.selectByCategory(category);
        Map<String, String> result = new HashMap<>();
        for (Setting setting : settings) {
            result.put(setting.getSettingKey(), setting.getSettingValue());
        }
        return result;
    }

    /**
     * 删除设置
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteSetting(String key) {
        Setting setting = settingMapper.selectByKey(key);
        if (setting == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "设置不存在");
        }
        setting.setDeletedAt(java.time.LocalDateTime.now());
        setting.setUpdatedAt(java.time.LocalDateTime.now());
        settingMapper.updateById(setting);

        // 清除缓存
        String cacheKey = CACHE_KEY_PREFIX + key;
        redisTemplate.delete(cacheKey);

        log.info("设置已删除: key={}", key);
    }

    /**
     * 检查注册是否启用
     */
    public boolean isRegistrationEnabled() {
        return getBoolean(KEY_REGISTRATION_ENABLED, true);
    }

    /**
     * 检查维护模式是否启用
     */
    public boolean isMaintenanceMode() {
        return getBoolean(KEY_MAINTENANCE_MODE, false);
    }

    /**
     * 获取默认分组 ID
     */
    public Long getDefaultGroupId() {
        String value = getValue(KEY_DEFAULT_GROUP_ID);
        if (value == null) {
            return 1L;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 1L;
        }
    }

    /**
     * 获取默认订阅分组 ID
     */
    public Long getDefaultSubscriptionGroupId() {
        String value = getValue(KEY_DEFAULT_SUBSCRIPTION_GROUP_ID);
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 获取 Claude Code 版本限制
     */
    public String getClaudeCodeVersionMin() {
        return getValue(KEY_CLAUDE_CODE_VERSION_MIN);
    }

    public String getClaudeCodeVersionMax() {
        return getValue(KEY_CLAUDE_CODE_VERSION_MAX);
    }

    /**
     * 确定值的类型
     */
    private String determineType(String value) {
        if (value == null) {
            return "string";
        }
        if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
            return "boolean";
        }
        try {
            Long.parseLong(value);
            return "number";
        } catch (NumberFormatException e) {
            // 不是整数，尝试浮点数
            try {
                Double.parseDouble(value);
                return "number";
            } catch (NumberFormatException ex) {
                // 不是数字，可能是 JSON
                if (value.startsWith("{") || value.startsWith("[")) {
                    return "json";
                }
                return "string";
            }
        }
    }

    /**
     * 清除所有设置缓存
     */
    public void clearCache() {
        Map<String, String> allSettings = getAllSettings();
        for (String key : allSettings.keySet()) {
            String cacheKey = CACHE_KEY_PREFIX + key;
            redisTemplate.delete(cacheKey);
        }
        log.info("已清除所有设置缓存");
    }
}
