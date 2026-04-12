package com.sub2api.module.common.service;

import com.sub2api.module.admin.service.SettingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

/**
 * 邮件服务
 * 提供邮件发送、验证码生成、密码重置等功能
 *
 * @author Alibaba Java Code Guidelines
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final SettingService settingService;

    private static final String SETTING_SMTP_HOST = "smtp_host";
    private static final String SETTING_SMTP_PORT = "smtp_port";
    private static final String SETTING_SMTP_USERNAME = "smtp_username";
    private static final String SETTING_SMTP_PASSWORD = "smtp_password";
    private static final String SETTING_SMTP_FROM = "smtp_from";
    private static final String SETTING_SMTP_FROM_NAME = "smtp_from_name";
    private static final String SETTING_SMTP_USE_TLS = "smtp_use_tls";

    private static final int VERIFY_CODE_LENGTH = 6;
    private static final long VERIFY_CODE_TTL_MINUTES = 15;
    private static final long VERIFY_CODE_COOLDOWN_MINUTES = 1;
    private static final int MAX_VERIFY_CODE_ATTEMPTS = 5;

    private static final long PASSWORD_RESET_TOKEN_TTL_MINUTES = 30;
    private static final long PASSWORD_RESET_EMAIL_COOLDOWN_SECONDS = 30;

    private static final String VERIFY_CODE_CACHE_PREFIX = "email:verify:";
    private static final String PASSWORD_RESET_TOKEN_CACHE_PREFIX = "email:reset:";
    private static final String PASSWORD_RESET_COOLDOWN_PREFIX = "email:reset:cooldown:";

    private final SecureRandom random = new SecureRandom();

    /**
     * SMTP 配置
     */
    @lombok.Data
    @lombok.experimental.Accessors(chain = true)
    public static class SMTPConfig {
        private String host;
        private int port;
        private String username;
        private String password;
        private String from;
        private String fromName;
        private boolean useTLS;
    }

    /**
     * 验证码数据
     */
    @lombok.Data
    @lombok.experimental.Accessors(chain = true)
    public static class VerificationCodeData {
        private String code;
        private int attempts;
        private LocalDateTime createdAt;
    }

    /**
     * 密码重置令牌数据
     */
    @lombok.Data
    @lombok.experimental.Accessors(chain = true)
    public static class PasswordResetTokenData {
        private String token;
        private LocalDateTime createdAt;
    }

    /**
     * 从数据库获取 SMTP 配置
     */
    public SMTPConfig getSMTPConfig() {
        Map<String, String> settings = settingService.getMultiple(
                SETTING_SMTP_HOST,
                SETTING_SMTP_PORT,
                SETTING_SMTP_USERNAME,
                SETTING_SMTP_PASSWORD,
                SETTING_SMTP_FROM,
                SETTING_SMTP_FROM_NAME,
                SETTING_SMTP_USE_TLS
        );

        String host = settings.get(SETTING_SMTP_HOST);
        if (host == null || host.isBlank()) {
            throw new ServiceUnavailableException("EMAIL_NOT_CONFIGURED", "email service not configured");
        }

        int port = 587;
        String portStr = settings.get(SETTING_SMTP_PORT);
        if (portStr != null && !portStr.isBlank()) {
            try {
                port = Integer.parseInt(portStr);
            } catch (NumberFormatException ignored) {
            }
        }

        boolean useTLS = "true".equalsIgnoreCase(settings.get(SETTING_SMTP_USE_TLS));

        return new SMTPConfig()
                .setHost(host.trim())
                .setPort(port)
                .setUsername(settings.getOrDefault(SETTING_SMTP_USERNAME, "").trim())
                .setPassword(settings.getOrDefault(SETTING_SMTP_PASSWORD, "").trim())
                .setFrom(settings.getOrDefault(SETTING_SMTP_FROM, "").trim())
                .setFromName(settings.getOrDefault(SETTING_SMTP_FROM_NAME, "").trim())
                .setUseTLS(useTLS);
    }

    /**
     * 发送邮件
     */
    public void sendEmail(String to, String subject, String body) {
        SMTPConfig config = getSMTPConfig();
        sendEmailWithConfig(config, to, subject, body);
    }

    /**
     * 使用指定配置发送邮件
     */
    public void sendEmailWithConfig(SMTPConfig config, String to, String subject, String body) {
        try {
            Properties props = new Properties();
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.starttls.enable", String.valueOf(config.isUseTLS()));
            props.put("mail.smtp.host", config.getHost());
            props.put("mail.smtp.port", String.valueOf(config.getPort()));

            Session session = Session.getInstance(props, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(config.getUsername(), config.getPassword());
                }
            });

            String from = config.getFrom();
            if (config.getFromName() != null && !config.getFromName().isBlank()) {
                from = config.getFromName() + " <" + config.getFrom() + ">";
            }

            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(from));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
            message.setSubject(subject);
            message.setContent(body, "text/html; charset=UTF-8");

            Transport.send(message);
            log.info("Email sent successfully to: {}", to);
        } catch (MessagingException e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage());
            throw new RuntimeException("Failed to send email", e);
        }
    }

    /**
     * 生成6位数字验证码
     */
    public String generateVerifyCode() {
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < VERIFY_CODE_LENGTH; i++) {
            code.append(random.nextInt(10));
        }
        return code.toString();
    }

    /**
     * 发送验证码邮件
     */
    public void sendVerifyCode(String email, String siteName) {
        // 检查是否在冷却期内
        String cacheKey = VERIFY_CODE_CACHE_PREFIX + email;
        VerificationCodeData existing = getVerificationCodeFromCache(cacheKey);

        if (existing != null) {
            long minutesSinceCreation = java.time.Duration.between(existing.getCreatedAt(), LocalDateTime.now()).toMinutes();
            if (minutesSinceCreation < VERIFY_CODE_COOLDOWN_MINUTES) {
                throw new ServiceUnavailableException("VERIFY_CODE_TOO_FREQUENT", "please wait before requesting a new code");
            }
        }

        // 生成验证码
        String code = generateVerifyCode();

        // 保存验证码
        VerificationCodeData data = new VerificationCodeData()
                .setCode(code)
                .setAttempts(0)
                .setCreatedAt(LocalDateTime.now());
        saveVerificationCodeToCache(cacheKey, data, VERIFY_CODE_TTL_MINUTES);

        // 构建邮件内容
        String subject = "[" + siteName + "] Email Verification Code";
        String body = buildVerifyCodeEmailBody(code, siteName);

        // 发送邮件
        sendEmail(email, subject, body);
    }

    /**
     * 验证验证码
     */
    public void verifyCode(String email, String code) {
        String cacheKey = VERIFY_CODE_CACHE_PREFIX + email;
        VerificationCodeData data = getVerificationCodeFromCache(cacheKey);

        if (data == null) {
            throw new BadRequestException("INVALID_VERIFY_CODE", "invalid or expired verification code");
        }

        // 检查是否已达到最大尝试次数
        if (data.getAttempts() >= MAX_VERIFY_CODE_ATTEMPTS) {
            throw new ServiceUnavailableException("VERIFY_CODE_MAX_ATTEMPTS", "too many failed attempts, please request a new code");
        }

        // 验证码不匹配
        if (!constantTimeEquals(data.getCode(), code)) {
            data.setAttempts(data.getAttempts() + 1);
            saveVerificationCodeToCache(cacheKey, data, VERIFY_CODE_TTL_MINUTES);

            if (data.getAttempts() >= MAX_VERIFY_CODE_ATTEMPTS) {
                throw new ServiceUnavailableException("VERIFY_CODE_MAX_ATTEMPTS", "too many failed attempts, please request a new code");
            }
            throw new BadRequestException("INVALID_VERIFY_CODE", "invalid or expired verification code");
        }

        // 验证成功，删除验证码
        deleteVerificationCodeFromCache(cacheKey);
    }

    /**
     * 生成密码重置令牌
     */
    public String generatePasswordResetToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        StringBuilder token = new StringBuilder();
        for (byte b : bytes) {
            token.append(String.format("%02x", b));
        }
        return token.toString();
    }

    /**
     * 发送密码重置邮件
     */
    public void sendPasswordResetEmail(String email, String siteName, String resetURL) {
        String token;
        boolean needSaveToken;

        // 检查是否已存在令牌
        String cacheKey = PASSWORD_RESET_TOKEN_CACHE_PREFIX + email;
        PasswordResetTokenData existing = getPasswordResetTokenFromCache(cacheKey);

        if (existing != null) {
            token = existing.getToken();
            needSaveToken = false;
        } else {
            token = generatePasswordResetToken();
            needSaveToken = true;
        }

        // 保存令牌
        if (needSaveToken) {
            PasswordResetTokenData data = new PasswordResetTokenData()
                    .setToken(token)
                    .setCreatedAt(LocalDateTime.now());
            savePasswordResetTokenToCache(cacheKey, data, PASSWORD_RESET_TOKEN_TTL_MINUTES);
        }

        // 构建完整的重置 URL
        String fullResetURL = resetURL + "?email=" + java.net.URLEncoder.encode(email, java.nio.charset.StandardCharsets.UTF_8)
                + "&token=" + java.net.URLEncoder.encode(token, java.nio.charset.StandardCharsets.UTF_8);

        // 构建邮件内容
        String subject = "[" + siteName + "] Password Reset Request";
        String body = buildPasswordResetEmailBody(fullResetURL, siteName);

        // 发送邮件
        sendEmail(email, subject, body);
    }

    /**
     * 发送密码重置邮件（带冷却期检查）
     */
    public void sendPasswordResetEmailWithCooldown(String email, String siteName, String resetURL) {
        // 检查冷却期
        String cooldownKey = PASSWORD_RESET_COOLDOWN_PREFIX + email;
        if (isInPasswordResetCooldown(cooldownKey)) {
            log.info("Password reset email skipped (cooldown): {}", email);
            return; // Silent success
        }

        // 发送邮件
        sendPasswordResetEmail(email, siteName, resetURL);

        // 设置冷却期
        setPasswordResetCooldown(cooldownKey, PASSWORD_RESET_EMAIL_COOLDOWN_SECONDS);
    }

    /**
     * 验证密码重置令牌
     */
    public void verifyPasswordResetToken(String email, String token) {
        String cacheKey = PASSWORD_RESET_TOKEN_CACHE_PREFIX + email;
        PasswordResetTokenData data = getPasswordResetTokenFromCache(cacheKey);

        if (data == null) {
            throw new BadRequestException("INVALID_RESET_TOKEN", "invalid or expired password reset token");
        }

        // 使用常量时间比较防止时序攻击
        if (!constantTimeEquals(data.getToken(), token)) {
            throw new BadRequestException("INVALID_RESET_TOKEN", "invalid or expired password reset token");
        }
    }

    /**
     * 消费密码重置令牌（验证后删除）
     */
    public void consumePasswordResetToken(String email, String token) {
        // 先验证
        verifyPasswordResetToken(email, token);

        // 删除令牌
        String cacheKey = PASSWORD_RESET_TOKEN_CACHE_PREFIX + email;
        deletePasswordResetTokenFromCache(cacheKey);
    }

    /**
     * 测试 SMTP 连接
     */
    public void testSMTPConnection(SMTPConfig config) {
        try {
            Properties props = new Properties();
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.starttls.enable", String.valueOf(config.isUseTLS()));
            props.put("mail.smtp.host", config.getHost());
            props.put("mail.smtp.port", String.valueOf(config.getPort()));

            Session session = Session.getInstance(props, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(config.getUsername(), config.getPassword());
                }
            });

            // 尝试连接
            Transport transport = session.getTransport("smtp");
            transport.connect(config.getHost(), config.getPort(), config.getUsername(), config.getPassword());
            transport.close();

        } catch (MessagingException e) {
            throw new RuntimeException("SMTP connection test failed: " + e.getMessage(), e);
        }
    }

    // ==================== 缓存操作 ====================

    /**
     * 从缓存获取验证码
     */
    protected VerificationCodeData getVerificationCodeFromCache(String key) {
        // TODO: 实现 Redis 缓存
        // 目前返回 null，后续集成 Redis
        return null;
    }

    /**
     * 保存验证码到缓存
     */
    protected void saveVerificationCodeToCache(String key, VerificationCodeData data, long ttlMinutes) {
        // TODO: 实现 Redis 缓存
        // 目前仅记录日志
        log.debug("Saving verification code to cache: key={}, ttl={}min", key, ttlMinutes);
    }

    /**
     * 从缓存删除验证码
     */
    protected void deleteVerificationCodeFromCache(String key) {
        // TODO: 实现 Redis 缓存
    }

    /**
     * 从缓存获取密码重置令牌
     */
    protected PasswordResetTokenData getPasswordResetTokenFromCache(String key) {
        // TODO: 实现 Redis 缓存
        return null;
    }

    /**
     * 保存密码重置令牌到缓存
     */
    protected void savePasswordResetTokenToCache(String key, PasswordResetTokenData data, long ttlMinutes) {
        // TODO: 实现 Redis 缓存
        log.debug("Saving password reset token to cache: key={}, ttl={}min", key, ttlMinutes);
    }

    /**
     * 从缓存删除密码重置令牌
     */
    protected void deletePasswordResetTokenFromCache(String key) {
        // TODO: 实现 Redis 缓存
    }

    /**
     * 检查密码重置邮件冷却期
     */
    protected boolean isInPasswordResetCooldown(String key) {
        // TODO: 实现 Redis 缓存
        return false;
    }

    /**
     * 设置密码重置邮件冷却期
     */
    protected void setPasswordResetCooldown(String key, long ttlSeconds) {
        // TODO: 实现 Redis 缓存
    }

    // ==================== 邮件模板 ====================

    /**
     * 构建验证码邮件内容
     */
    private String buildVerifyCodeEmailBody(String code, String siteName) {
        return String.format("""
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <style>
        body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, sans-serif; background-color: #f5f5f5; margin: 0; padding: 20px; }
        .container { max-width: 600px; margin: 0 auto; background-color: #ffffff; border-radius: 8px; overflow: hidden; box-shadow: 0 2px 8px rgba(0,0,0,0.1); }
        .header { background: linear-gradient(135deg, #667eea 0%%, #764ba2 100%%); color: white; padding: 30px; text-align: center; }
        .header h1 { margin: 0; font-size: 24px; }
        .content { padding: 40px 30px; text-align: center; }
        .code { font-size: 36px; font-weight: bold; letter-spacing: 8px; color: #333; background-color: #f8f9fa; padding: 20px 30px; border-radius: 8px; display: inline-block; margin: 20px 0; font-family: monospace; }
        .info { color: #666; font-size: 14px; line-height: 1.6; margin-top: 20px; }
        .footer { background-color: #f8f9fa; padding: 20px; text-align: center; color: #999; font-size: 12px; }
    </style>
</head>
<body>
    <div class="container">
        <div class="header">
            <h1>%s</h1>
        </div>
        <div class="content">
            <p style="font-size: 18px; color: #333;">Your verification code is:</p>
            <div class="code">%s</div>
            <div class="info">
                <p>This code will expire in <strong>15 minutes</strong>.</p>
                <p>If you did not request this code, please ignore this email.</p>
            </div>
        </div>
        <div class="footer">
            <p>This is an automated message, please do not reply.</p>
        </div>
    </div>
</body>
</html>
""", siteName, code);
    }

    /**
     * 构建密码重置邮件内容
     */
    private String buildPasswordResetEmailBody(String resetURL, String siteName) {
        return String.format("""
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <style>
        body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, sans-serif; background-color: #f5f5f5; margin: 0; padding: 20px; }
        .container { max-width: 600px; margin: 0 auto; background-color: #ffffff; border-radius: 8px; overflow: hidden; box-shadow: 0 2px 8px rgba(0,0,0,0.1); }
        .header { background: linear-gradient(135deg, #667eea 0%%, #764ba2 100%%); color: white; padding: 30px; text-align: center; }
        .header h1 { margin: 0; font-size: 24px; }
        .content { padding: 40px 30px; text-align: center; }
        .button { display: inline-block; background: linear-gradient(135deg, #667eea 0%%, #764ba2 100%%); color: white; padding: 14px 32px; text-decoration: none; border-radius: 8px; font-size: 16px; font-weight: 600; margin: 20px 0; }
        .button:hover { opacity: 0.9; }
        .info { color: #666; font-size: 14px; line-height: 1.6; margin-top: 20px; }
        .link-fallback { color: #666; font-size: 12px; word-break: break-all; margin-top: 20px; padding: 15px; background-color: #f8f9fa; border-radius: 4px; }
        .footer { background-color: #f8f9fa; padding: 20px; text-align: center; color: #999; font-size: 12px; }
        .warning { color: #e74c3c; font-weight: 500; }
    </style>
</head>
<body>
    <div class="container">
        <div class="header">
            <h1>%s</h1>
        </div>
        <div class="content">
            <p style="font-size: 18px; color: #333;">Password Reset Request</p>
            <p style="color: #666;">You have requested to reset your password. Please click the button below to set a new password:</p>
            <a href="%s" class="button">Reset Password</a>
            <div class="info">
                <p>This link will expire in <strong>30 minutes</strong>.</p>
                <p class="warning">If you did not request a password reset, please ignore this email. Your password will remain unchanged.</p>
            </div>
            <div class="link-fallback">
                <p>If the button does not work, please copy and paste the following link into your browser:</p>
                <p>%s</p>
            </div>
        </div>
        <div class="footer">
            <p>This is an automated message, please do not reply.</p>
        </div>
    </div>
</body>
</html>
""", siteName, resetURL, resetURL);
    }

    /**
     * 常量时间字符串比较，防止时序攻击
     */
    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        if (a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }

    /**
     * 服务不可用异常
     */
    public static class ServiceUnavailableException extends RuntimeException {
        private final String errorCode;

        public ServiceUnavailableException(String errorCode, String message) {
            super(message);
            this.errorCode = errorCode;
        }

        public String getErrorCode() {
            return errorCode;
        }
    }

    /**
     * 错误请求异常
     */
    public static class BadRequestException extends RuntimeException {
        private final String errorCode;

        public BadRequestException(String errorCode, String message) {
            super(message);
            this.errorCode = errorCode;
        }

        public String getErrorCode() {
            return errorCode;
        }
    }
}
