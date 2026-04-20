package com.sub2api.module.admin.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 错误透传规则实体
 * 表名: error_passthrough_rules
 *
 * @author Alibaba Java Code Guidelines
 */
@Data
@Accessors(chain = true)
@TableName("error_passthrough_rules")
public class ErrorPassthroughRule implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 规则ID
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 规则名称
     */
    private String name;

    /**
     * 是否启用
     */
    private Boolean enabled;

    /**
     * 优先级（数字越小优先级越高）
     */
    private Integer priority;

    /**
     * 匹配的错误码列表（OR关系）
     */
    private List<Integer> errorCodes;

    /**
     * 匹配的关键词列表（OR关系）
     */
    private List<String> keywords;

    /**
     * 匹配模式: "any"(任一条件) 或 "all"(所有条件)
     */
    private String matchMode;

    /**
     * 适用平台列表
     */
    private List<String> platforms;

    /**
     * 是否透传上游原始状态码
     */
    private Boolean passthroughCode;

    /**
     * 自定义响应状态码（passthrough_code=false 时使用）
     */
    private Integer responseCode;

    /**
     * 是否透传上游原始错误信息
     */
    private Boolean passthroughBody;

    /**
     * 自定义错误信息（passthrough_body=false 时使用）
     */
    private String customMessage;

    /**
     * 是否跳过运维监控记录
     */
    private Boolean skipMonitoring;

    /**
     * 规则描述
     */
    private String description;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedAt;

    /**
     * 删除时间 (软删除)
     */
    private OffsetDateTime deletedAt;

    // ==================== 常量定义 ====================

    /**
     * 匹配模式：任一条件满足即可
     */
    public static final String MATCH_MODE_ANY = "any";

    /**
     * 匹配模式：所有条件都必须满足
     */
    public static final String MATCH_MODE_ALL = "all";

    /**
     * 支持的平台：Anthropic
     */
    public static final String PLATFORM_ANTHROPIC = "anthropic";

    /**
     * 支持的平台：OpenAI
     */
    public static final String PLATFORM_OPENAI = "openai";

    /**
     * 支持的平台：Gemini
     */
    public static final String PLATFORM_GEMINI = "gemini";

    /**
     * 支持的平台：Antigravity
     */
    public static final String PLATFORM_ANTIGRAVITY = "antigravity";

    // ==================== 辅助方法 ====================

    /**
     * 是否为 any 匹配模式
     */
    public boolean isMatchModeAny() {
        return MATCH_MODE_ANY.equals(matchMode);
    }

    /**
     * 是否为 all 匹配模式
     */
    public boolean isMatchModeAll() {
        return MATCH_MODE_ALL.equals(matchMode);
    }

    /**
     * 是否配置了错误码条件
     */
    public boolean hasErrorCodes() {
        return errorCodes != null && !errorCodes.isEmpty();
    }

    /**
     * 是否配置了关键词条件
     */
    public boolean hasKeywords() {
        return keywords != null && !keywords.isEmpty();
    }

    /**
     * 是否适用于所有平台
     */
    public boolean isAllPlatforms() {
        return platforms == null || platforms.isEmpty();
    }

    /**
     * 检查是否适用于指定平台
     */
    public boolean appliesTo(String platform) {
        if (isAllPlatforms()) {
            return true;
        }
        if (platform == null) {
            return false;
        }
        String lowerPlatform = platform.toLowerCase();
        for (String p : platforms) {
            if (p.toLowerCase().equals(lowerPlatform)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查是否包含指定错误码
     */
    public boolean containsErrorCode(int code) {
        if (!hasErrorCodes()) {
            return false;
        }
        for (Integer errorCode : errorCodes) {
            if (errorCode == code) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查是否包含指定关键词（不区分大小写）
     */
    public boolean containsKeyword(String text) {
        if (!hasKeywords() || text == null) {
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
}
