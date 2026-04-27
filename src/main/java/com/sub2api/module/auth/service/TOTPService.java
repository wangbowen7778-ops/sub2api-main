package com.sub2api.module.auth.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.concurrent.TimeUnit;

/**
 * TOTP 双因素认证服务
 *
 * @author Alibaba Java Code Guidelines
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TOTPService {

    private static final String ALGORITHM = "HmacSHA1";
    private static final int CODE_DIGITS = 6;
    private static final int TIME_STEP_SECONDS = 30;
    private static final int SECRET_KEY_LENGTH = 20;
    private static final String TOTP_SETUP_KEY_PREFIX = "totp:setup:";
    private static final int TOTP_SETUP_TTL_MINUTES = 5;
    public static final String TOTP_ISSUER = "Sub2API";

    private final SecureRandom secureRandom = new SecureRandom();
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 生成 TOTP 密钥
     */
    public String generateSecret() {
        byte[] bytes = new byte[SECRET_KEY_LENGTH];
        secureRandom.nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    /**
     * 生成当前时间步的 TOTP 代码
     */
    public String generateCode(String secret) {
        return generateCode(secret, getCurrentTimeStep());
    }

    /**
     * 生成指定时间步的 TOTP 代码
     */
    public String generateCode(String secret, long timeStep) {
        try {
            byte[] secretBytes = Base64.getDecoder().decode(secret);
            byte[] timeBytes = ByteBuffer.allocate(8).putLong(timeStep).array();

            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secretBytes, ALGORITHM));
            byte[] hash = mac.doFinal(timeBytes);

            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24)
                    | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8)
                    | (hash[offset + 3] & 0xFF);

            int otp = binary % (int) Math.pow(10, CODE_DIGITS);
            return String.format("%0" + CODE_DIGITS + "d", otp);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("生成 TOTP 代码失败", e);
            throw new RuntimeException("生成 TOTP 代码失败", e);
        }
    }

    /**
     * 验证 TOTP 代码
     */
    public boolean verifyCode(String secret, String code) {
        return verifyCode(secret, code, getCurrentTimeStep());
    }

    /**
     * 验证 TOTP 代码 (支持时间偏移)
     */
    public boolean verifyCode(String secret, String code, long timeStep) {
        // 允许前后各 1 个时间步的误差
        for (int i = -1; i <= 1; i++) {
            String expectedCode = generateCode(secret, timeStep + i);
            if (expectedCode.equals(code)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取当前时间步
     */
    public long getCurrentTimeStep() {
        return System.currentTimeMillis() / 1000 / TIME_STEP_SECONDS;
    }

    /**
     * 获取密钥的 Base32 编码 (用于生成二维码)
     */
    public String getBase32Secret(String secret) {
        return Base64.getEncoder().encodeToString(secret.getBytes()).replace("=", "").toUpperCase();
    }

    /**
     * 生成 otpauth:// URI
     */
    public String generateOtpauthUri(String secret, String accountName, String issuer) {
        String base32Secret = getBase32Secret(secret);
        String encodedIssuer = issuer != null ? issuer : TOTP_ISSUER;
        return String.format(
                "otpauth://totp/%s:%s?secret=%s&issuer=%s&digits=%d&period=%d",
                encodedIssuer, accountName, base32Secret, encodedIssuer, CODE_DIGITS, TIME_STEP_SECONDS
        );
    }

    /**
     * TOTP Setup Session
     */
    public static class TotpSetupSession {
        public String secret;
        public String setupToken;
        public long createdAt;

        public TotpSetupSession() {}

        public TotpSetupSession(String secret, String setupToken) {
            this.secret = secret;
            this.setupToken = setupToken;
            this.createdAt = System.currentTimeMillis();
        }
    }

    /**
     * 生成随机 token
     */
    public String generateRandomToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    /**
     * 存储 TOTP setup session 到 Redis
     */
    public void setSetupSession(Long userId, TotpSetupSession session) {
        try {
            String key = TOTP_SETUP_KEY_PREFIX + userId;
            String value = objectMapper.writeValueAsString(session);
            redisTemplate.opsForValue().set(key, value, TOTP_SETUP_TTL_MINUTES, TimeUnit.MINUTES);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize TOTP session", e);
        }
    }

    /**
     * 获取 TOTP setup session
     */
    public TotpSetupSession getSetupSession(Long userId) {
        String key = TOTP_SETUP_KEY_PREFIX + userId;
        String value = redisTemplate.opsForValue().get(key);
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.readValue(value, TotpSetupSession.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize TOTP session", e);
        }
    }

    /**
     * 删除 TOTP setup session
     */
    public void deleteSetupSession(Long userId) {
        String key = TOTP_SETUP_KEY_PREFIX + userId;
        redisTemplate.delete(key);
    }
}
