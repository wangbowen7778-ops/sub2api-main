package com.sub2api.module.billing.model.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * 使用记录清理过滤器
 * 定义清理任务过滤条件
 *
 * @author Sub2API
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UsageCleanupFilters implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 开始时间（必填）
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private OffsetDateTime startTime;

    /**
     * 结束时间（必填）
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private OffsetDateTime endTime;

    /**
     * 用户ID（可选）
     */
    private Long userId;

    /**
     * API Key ID（可选）
     */
    private Long apiKeyId;

    /**
     * 账号ID（可选）
     */
    private Long accountId;

    /**
     * 分组ID（可选）
     */
    private Long groupId;

    /**
     * 模型名称（可选）
     */
    private String model;

    /**
     * 请求类型（可选）
     */
    private String requestType;

    /**
     * 是否流式（可选）
     */
    private Boolean stream;

    /**
     * 计费类型（可选）
     */
    private Integer billingType;
}
