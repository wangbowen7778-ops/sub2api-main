package com.sub2api.module.admin.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 定时测试结果实体
 *
 * @author Alibaba Java Code Guidelines
 */
@Data
@Accessors(chain = true)
@TableName("scheduled_test_results")
public class ScheduledTestResult implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 结果ID
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 计划ID
     */
    private Long planId;

    /**
     * 执行时间
     */
    private OffsetDateTime executedAt;

    /**
     * 延迟 (ms)
     */
    private Integer latencyMs;

    /**
     * 输入 tokens
     */
    private Integer inputTokens;

    /**
     * 输出 tokens
     */
    private Integer outputTokens;

    /**
     * 成本
     */
    private BigDecimal cost;

    /**
     * 是否成功
     */
    private Boolean success;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 响应状态码
     */
    private Integer statusCode;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;
}
