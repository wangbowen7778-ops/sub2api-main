package com.sub2api.module.gateway.service;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * 上游故障转移错误
 * 当请求上游服务失败时抛出此错误，触发故障转移逻辑
 *
 * @author Alibaba Java Code Guidelines
 */
@Slf4j
@Data
public class UpstreamFailoverError extends RuntimeException {

    /**
     * HTTP 状态码
     */
    private final int statusCode;

    /**
     * 上游响应体
     */
    private final String responseBody;

    /**
     * 上游响应头
     */
    private final java.util.Map<String, String> responseHeaders;

    /**
     * 是否强制缓存计费（Antigravity 粘性会话切换时设为 true）
     */
    private boolean forceCacheBilling;

    /**
     * 是否可在同一账号上重试
     * 临时性错误（如 Google 间歇性 400，空响应）应设为 true
     */
    private boolean retryableOnSameAccount;

    public UpstreamFailoverError(int statusCode, String responseBody) {
        this(statusCode, responseBody, null);
    }

    public UpstreamFailoverError(int statusCode, String responseBody, java.util.Map<String, String> responseHeaders) {
        super(String.format("upstream error: %d (failover)", statusCode));
        this.statusCode = statusCode;
        this.responseBody = responseBody;
        this.responseHeaders = responseHeaders;
    }

    /**
     * 快速创建 500 错误
     */
    public static UpstreamFailoverError serverError(String responseBody) {
        return new UpstreamFailoverError(500, responseBody);
    }

    /**
     * 快速创建 502 错误
     */
    public static UpstreamFailoverError badGateway(String responseBody) {
        return new UpstreamFailoverError(502, responseBody);
    }

    /**
     * 快速创建 503 错误
     */
    public static UpstreamFailoverError serviceUnavailable(String responseBody) {
        return new UpstreamFailoverError(503, responseBody);
    }

    /**
     * 快速创建 504 错误
     */
    public static UpstreamFailoverError gatewayTimeout(String responseBody) {
        return new UpstreamFailoverError(504, responseBody);
    }

    /**
     * 判断是否是服务器错误（5xx）
     */
    public boolean isServerError() {
        return statusCode >= 500 && statusCode < 600;
    }

    /**
     * 判断是否是客户端错误（4xx）
     */
    public boolean isClientError() {
        return statusCode >= 400 && statusCode < 500;
    }

    /**
     * 判断是否是临时性错误，应该重试
     */
    public boolean isTemporaryError() {
        // 429 Too Many Requests, 500, 502, 503, 504 都是临时性错误
        return statusCode == 429 || statusCode == 500 || statusCode == 502 || statusCode == 503 || statusCode == 504;
    }

    /**
     * 获取响应头中的特定值
     */
    public String getResponseHeader(String name) {
        if (responseHeaders == null) {
            return null;
        }
        return responseHeaders.get(name);
    }
}
