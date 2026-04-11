package com.sub2api.module.account.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 分组实体
 * 表名: groups
 *
 * @author Alibaba Java Code Guidelines
 */
@Data
@Accessors(chain = true)
@TableName("groups")
public class Group implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 分组ID
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 分组名称
     */
    private String name;

    /**
     * 描述
     */
    private String description;

    /**
     * 计费倍率
     */
    private BigDecimal rateMultiplier;

    /**
     * 是否独占
     */
    private Boolean isExclusive;

    /**
     * 状态: active, disabled
     */
    private String status;

    /**
     * 平台
     */
    private String platform;

    /**
     * 订阅类型
     */
    private String subscriptionType;

    /**
     * 每日限额 (USD)
     */
    private BigDecimal dailyLimitUsd;

    /**
     * 每周限额 (USD)
     */
    private BigDecimal weeklyLimitUsd;

    /**
     * 每月限额 (USD)
     */
    private BigDecimal monthlyLimitUsd;

    /**
     * 默认有效期天数
     */
    private Integer defaultValidityDays;

    /**
     * 图片生成计费配置 - 1K
     */
    private BigDecimal imagePrice1k;

    /**
     * 图片生成计费配置 - 2K
     */
    private BigDecimal imagePrice2k;

    /**
     * 图片生成计费配置 - 4K
     */
    private BigDecimal imagePrice4k;

    /**
     * 是否仅允许 Claude Code 客户端
     */
    private Boolean claudeCodeOnly;

    /**
     * 非 Claude Code 请求降级使用的分组 ID
     */
    private Long fallbackGroupId;

    /**
     * 无效请求兜底使用的分组 ID
     */
    private Long fallbackGroupIdOnInvalidRequest;

    /**
     * 模型路由配置 (JSONB)
     */
    private Map<String, List<Long>> modelRouting;

    /**
     * 是否启用模型路由配置
     */
    private Boolean modelRoutingEnabled;

    /**
     * 是否注入 MCP XML 调用协议提示词
     */
    private Boolean mcpXmlInject;

    /**
     * 支持的模型系列 (JSONB)
     */
    private List<String> supportedModelScopes;

    /**
     * 分组显示排序
     */
    private Integer sortOrder;

    /**
     * 是否允许 /v1/messages 调度到此分组
     */
    private Boolean allowMessagesDispatch;

    /**
     * 仅允许非 apikey 类型账号关联到此分组
     */
    private Boolean requireOauthOnly;

    /**
     * 调度时仅允许 privacy 已成功设置的账号
     */
    private Boolean requirePrivacySet;

    /**
     * 默认映射模型 ID
     */
    private String defaultMappedModel;

    /**
     * OpenAI Messages 调度模型配置 (JSONB)
     */
    private Map<String, Object> messagesDispatchModelConfig;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /**
     * 删除时间 (软删除)
     */
    private LocalDateTime deletedAt;
}
