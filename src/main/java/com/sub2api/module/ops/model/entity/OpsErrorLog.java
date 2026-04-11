package com.sub2api.module.ops.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Ops 错误日志实体
 * 表名: ops_error_logs
 */
@Data
@Accessors(chain = true)
@TableName("ops_error_logs")
public class OpsErrorLog implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 日志ID
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 错误阶段: proxy, upstream, billing, auth, etc.
     */
    private String errorPhase;

    /**
     * 错误代码
     */
    private String errorCode;

    /**
     * HTTP 状态码
     */
    private Integer statusCode;

    /**
     * 上游响应时间 (ms)
     */
    private Integer upstreamLatencyMs;

    /**
     * 总响应时间 (ms)
     */
    private Integer totalLatencyMs;

    /**
     * 平台: anthropic, openai, google, antigravity
     */
    private String platform;

    /**
     * 模型
     */
    private String model;

    /**
     * 账号ID
     */
    private Long accountId;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * API Key ID
     */
    private Long apiKeyId;

    /**
     * 分组ID
     */
    private Long groupId;

    /**
     * 渠道ID
     */
    private Long channelId;

    /**
     * 错误消息
     */
    private String errorMessage;

    /**
     * 错误详情 (JSON)
     */
    private String errorDetail;

    /**
     * 请求路径
     */
    private String requestPath;

    /**
     * 请求方法
     */
    private String requestMethod;

    /**
     * 客户端IP
     */
    private String clientIp;

    /**
     * 请求头 (JSON)
     */
    private String requestHeaders;

    /**
     * 请求体 (JSON, 已脱敏)
     */
    private String requestBody;

    /**
     * 响应头 (JSON)
     */
    private String responseHeaders;

    /**
     * 响应体 (JSON)
     */
    private String responseBody;

    /**
     * 是否已处理
     */
    private Boolean handled;

    /**
     * 处理备注
     */
    private String handlingNote;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
