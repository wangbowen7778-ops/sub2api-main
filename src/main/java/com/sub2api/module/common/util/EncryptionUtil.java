package com.sub2api.module.common.util;

import cn.hutool.core.codec.Base64;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.spec.AlgorithmParameterSpec;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * 加密工具类
 *
 * @author Alibaba Java Code Guidelines
 */
public class EncryptionUtil {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 16;
    private static final int GCM_IV_LENGTH = 12;

    private EncryptionUtil() {
    }

    /**
     * 生成 AES 密钥
     */
    public static String generateAesKey() throws Exception {
        KeyGenerator keyGen = KeyGenerator.getInstance(ALGORITHM);
        keyGen.init(128);
        SecretKey key = keyGen.generateKey();
        return Base64.encode(key.getEncoded());
    }

    /**
     * AES 加密
     */
    public static String aesEncrypt(String content, String key) throws Exception {
        byte[] keyBytes = Base64.decode(key);
        SecretKeySpec keySpec = new SecretKeySpec(keyBytes, ALGORITHM);
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, keySpec);
        byte[] encrypted = cipher.doFinal(content.getBytes(StandardCharsets.UTF_8));
        return Base64.encode(encrypted);
    }

    /**
     * AES 解密
     */
    public static String aesDecrypt(String encrypted, String key) throws Exception {
        byte[] keyBytes = Base64.decode(key);
        SecretKeySpec keySpec = new SecretKeySpec(keyBytes, ALGORITHM);
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, keySpec);
        byte[] decrypted = cipher.doFinal(Base64.decode(encrypted));
        return new String(decrypted, StandardCharsets.UTF_8);
    }

    /**
     * AES-GCM 加密
     */
    public static String aesGcmEncrypt(String content, String key) {
        try {
            byte[] keyBytes = Base64.decode(key);
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);
            cipher.init(Cipher.ENCRYPT_MODE,
                    new SecretKeySpec(keyBytes, ALGORITHM), gcmSpec);

            byte[] encrypted = cipher.doFinal(content.getBytes(StandardCharsets.UTF_8));

            // 拼接 IV 和密文
            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);

            return Base64.encode(combined);
        } catch (Exception e) {
            throw new RuntimeException("AES-GCM encryption failed", e);
        }
    }

    /**
     * AES-GCM 解密
     */
    public static String aesGcmDecrypt(String encryptedContent, String key) {
        try {
            byte[] combined = Base64.decode(encryptedContent);
            byte[] keyBytes = Base64.decode(key);

            // 分离 IV 和密文
            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] encrypted = new byte[combined.length - GCM_IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, iv.length);
            System.arraycopy(combined, iv.length, encrypted, 0, encrypted.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);
            cipher.init(Cipher.DECRYPT_MODE,
                    new SecretKeySpec(keyBytes, ALGORITHM), gcmSpec);

            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("AES-GCM decryption failed", e);
        }
    }

    /**
     * MD5 加密
     */
    public static String md5(String text) {
        return hash(text, "MD5");
    }

    /**
     * SHA256 加密
     */
    public static String sha256(String text) {
        return hash(text, "SHA-256");
    }

    /**
     * 密码加盐哈希
     */
    public static String hashPassword(String password, String salt) {
        return sha256(password + salt);
    }

    /**
     * 验证密码
     */
    public static boolean verifyPassword(String password, String salt, String hashedPassword) {
        return hashPassword(password, salt).equals(hashedPassword);
    }

    /**
     * 生成随机盐
     */
    public static String generateSalt() {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        return Base64.encode(salt);
    }

    /**
     * SHA256 加密 (JDK 实现)
     */
    public static String sha256Jdk(String text) {
        return sha256(text);
    }

    private static String hash(String text, String algorithm) {
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("Hash failed", e);
        }
    }
}
