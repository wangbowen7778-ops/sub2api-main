package com.sub2api.module.account.service.scheduler;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 调度分桶
 * 用于标识一组特定的账号调度配置
 *
 * @author Sub2API
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SchedulerBucket {

    /**
     * 分组ID
     */
    private Long groupId;

    /**
     * 平台 (anthropic, gemini, openai, antigravity)
     */
    private String platform;

    /**
     * 调度模式: single, mixed, forced
     */
    private String mode;

    /**
     * 转换为字符串格式: groupId:platform:mode
     */
    @Override
    public String toString() {
        return groupId + ":" + platform + ":" + mode;
    }

    /**
     * 从字符串解析分桶
     *
     * @param raw 格式: groupId:platform:mode
     * @return 分桶对象，如果格式无效返回 null
     */
    public static SchedulerBucket parse(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        String[] parts = raw.split(":");
        if (parts.length != 3) {
            return null;
        }
        try {
            Long groupId = Long.parseLong(parts[0]);
            String platform = parts[1];
            String mode = parts[2];
            if (platform.isEmpty() || mode.isEmpty()) {
                return null;
            }
            return new SchedulerBucket(groupId, platform, mode);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 创建单一模式分桶
     */
    public static SchedulerBucket single(Long groupId, String platform) {
        return new SchedulerBucket(groupId, platform, SchedulerMode.SINGLE);
    }

    /**
     * 创建混合模式分桶
     */
    public static SchedulerBucket mixed(Long groupId, String platform) {
        return new SchedulerBucket(groupId, platform, SchedulerMode.MIXED);
    }

    /**
     * 创建强制模式分桶
     */
    public static SchedulerBucket forced(Long groupId, String platform) {
        return new SchedulerBucket(groupId, platform, SchedulerMode.FORCED);
    }
}
