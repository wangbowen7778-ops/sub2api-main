package com.sub2api.module.common.service;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Cloudflare Turnstile 验证服务
 * 用于注册和登录时的机器人验证
 *
 * @author Alibaba Java Code Guidelines
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TurnstileService {

    private static final String VERIFY_URL = "https://challenges.cloudflare.com/turnstile/v0/siteverify";

    private final HttpClient httpClient;

    @Value("${turnstile.secret-key:}")
    private String defaultSecretKey;

    /**
     * Turnstile 验证响应
     */
    @Data
    public static class TurnstileVerifyResponse {
        private boolean success;
        private String challengeTs;
        private String hostname;
        private Object errorCodes;
        private Object action;
        private Object cdata;
    }

    /**
     * 验证 Turnstile token
     *
     * @param secretKey 密钥
     * @param token     前端提交的 token
     * @param remoteIP  用户 IP（可选）
     * @return 验证结果
     */
    public TurnstileVerifyResponse verifyToken(String secretKey, String token, String remoteIP) {
        if (token == null || token.isBlank()) {
            return createErrorResponse("missing-input-response");
        }

        String actualSecretKey = secretKey != null && !secretKey.isBlank() ? secretKey : defaultSecretKey;
        if (actualSecretKey == null || actualSecretKey.isBlank()) {
            log.warn("Turnstile secret key not configured");
            // 在未配置密钥时验证通过（方便测试）
            TurnstileVerifyResponse response = new TurnstileVerifyResponse();
            response.setSuccess(true);
            return response;
        }

        try {
            // 构建表单数据
            String formData = "secret=" + URI.encode(actualSecretKey) +
                    "&response=" + URI.encode(token);
            if (remoteIP != null && !remoteIP.isBlank()) {
                formData += "&remoteip=" + URI.encode(remoteIP);
            }

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(VERIFY_URL))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(formData))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.warn("Turnstile verify request failed: status={}", response.statusCode());
                return createErrorResponse("verify-request-failed");
            }

            return parseResponse(response.body());

        } catch (Exception e) {
            log.error("Turnstile verify error: {}", e.getMessage());
            return createErrorResponse("verify-error");
        }
    }

    /**
     * 验证 Turnstile token（使用默认密钥）
     */
    public TurnstileVerifyResponse verifyToken(String token, String remoteIP) {
        return verifyToken(null, token, remoteIP);
    }

    /**
     * 解析验证响应
     */
    private TurnstileVerifyResponse parseResponse(String body) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.readValue(body, TurnstileVerifyResponse.class);
        } catch (Exception e) {
            log.error("Failed to parse Turnstile response: {}", e.getMessage());
            return createErrorResponse("parse-error");
        }
    }

    /**
     * 创建错误响应
     */
    private TurnstileVerifyResponse createErrorResponse(String errorCode) {
        TurnstileVerifyResponse response = new TurnstileVerifyResponse();
        response.setSuccess(false);
        response.setErrorCodes(new String[]{errorCode});
        return response;
    }
}
