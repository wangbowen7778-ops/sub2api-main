package com.sub2api.module.account.service.scheduler;

/**
 * 调度模式常量
 *
 * @author Sub2API
 */
public final class SchedulerMode {

    private SchedulerMode() {
    }

    /**
     * 单一模式 - 只调度指定平台的账号
     */
    public static final String SINGLE = "single";

    /**
     * 混合模式 - 调度多个平台的账号（用于 Anthropic 和 Gemini）
     */
    public static final String MIXED = "mixed";

    /**
     * 强制模式 - 强制调度指定平台的账号
     */
    public static final String FORCED = "forced";
}
