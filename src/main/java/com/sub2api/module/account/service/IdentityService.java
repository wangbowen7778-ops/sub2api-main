package com.sub2api.module.account.service;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 身份服务
 * 管理 OAuth 账号的请求身份指纹 (User-Agent 等)
 *
 * @author Alibaba Java Code Guidelines
 */
@Slf4j
@Service
public class IdentityService {

    private static final String FINGERPRINT_CACHE_PREFIX = "identity:fingerprint:";
    private static final String MASKED_SESSION_CACHE_PREFIX = "identity:masked_session:";
    private static final long FINGERPRINT_TTL_DAYS = 7;
    private static final long MASKED_SESSION_TTL_MINUTES = 15;
    private static final long TTL_REFRESH_INTERVAL_HOURS = 24;

    /**
     * 默认指纹值（当客户端未提供时使用）
     */
    private static final Fingerprint DEFAULT_FINGERPRINT = new Fingerprint()
            .setUserAgent("claude-cli/2.1.22 (external, cli)")
            .setStainlessLang("js")
            .setStainlessPackageVersion("0.70.0")
            .setStainlessOS("Linux")
            .setStainlessArch("arm64")
            .setStainlessRuntime("node")
            .setStainlessRuntimeVersion("v24.13.0");

    /**
     * 指纹数据
     */
    @Data
    @lombok.experimental.Accessors(chain = true)
    public static class Fingerprint {
        private String clientId;
        private String userAgent;
        private String stainlessLang;
        private String stainlessPackageVersion;
        private String stainlessOS;
        private String stainlessArch;
        private String stainlessRuntime;
        private String stainlessRuntimeVersion;
        private long updatedAt; // Unix timestamp
    }

    /**
     * 身份缓存接口
     */
    public interface IdentityCache {
        /**
         * 获取指纹
         */
        Fingerprint getFingerprint(long accountId);

        /**
         * 设置指纹
         */
        void setFingerprint(long accountId, Fingerprint fingerprint);

        /**
         * 获取伪装的会话ID
         */
        String getMaskedSessionId(long accountId);

        /**
         * 设置伪装的会话ID
         */
        void setMaskedSessionId(long accountId, String sessionId);
    }

    private final IdentityCache cache;

    public IdentityService(IdentityCache cache) {
        this.cache = cache;
    }

    /**
     * 获取或创建账号的指纹
     */
    public Fingerprint getOrCreateFingerprint(long accountId, java.util.Map<String, String> headers) {
        // 尝试从缓存获取指纹
        Fingerprint cached = cache.getFingerprint(accountId);
        if (cached != null) {
            boolean needWrite = false;

            // 检查客户端的 user-agent 是否是更新版本
            String clientUA = headers != null ? headers.get("User-Agent") : null;
            if (clientUA != null && !clientUA.isEmpty() && isNewerVersion(clientUA, cached.getUserAgent())) {
                // 版本升级：merge 语义
                mergeHeadersIntoFingerprint(cached, headers);
                needWrite = true;
                log.info("Updated fingerprint for account {}: {} (merge update)", accountId, clientUA);
            } else if (System.currentTimeMillis() / 1000 - cached.getUpdatedAt() > TTL_REFRESH_INTERVAL_HOURS * 3600) {
                // 距上次写入超过24小时，续期TTL
                needWrite = true;
            }

            if (needWrite) {
                cached.setUpdatedAt(System.currentTimeMillis() / 1000);
                cache.setFingerprint(accountId, cached);
            }

            return cached;
        }

        // 缓存不存在，创建新指纹
        Fingerprint fp = createFingerprintFromHeaders(headers);
        fp.setClientId(generateClientId());
        fp.setUpdatedAt(System.currentTimeMillis() / 1000);
        cache.setFingerprint(accountId, fp);

        return fp;
    }

    /**
     * 获取指纹（如果不存在则返回默认指纹）
     */
    public Fingerprint getFingerprint(long accountId) {
        Fingerprint fp = cache.getFingerprint(accountId);
        return fp != null ? fp : DEFAULT_FINGERPRINT;
    }

    /**
     * 设置指纹
     */
    public void setFingerprint(long accountId, Fingerprint fingerprint) {
        fingerprint.setUpdatedAt(System.currentTimeMillis() / 1000);
        cache.setFingerprint(accountId, fingerprint);
    }

    /**
     * 获取伪装的会话ID
     */
    public String getMaskedSessionId(long accountId) {
        return cache.getMaskedSessionId(accountId);
    }

    /**
     * 设置伪装的会话ID
     */
    public void setMaskedSessionId(long accountId, String sessionId) {
        cache.setMaskedSessionId(accountId, sessionId);
    }

    /**
     * 从请求头创建指纹
     */
    private Fingerprint createFingerprintFromHeaders(java.util.Map<String, String> headers) {
        Fingerprint fp = new Fingerprint();

        fp.setUserAgent(getHeaderOrDefault(headers, "User-Agent", DEFAULT_FINGERPRINT.getUserAgent()));
        fp.setStainlessLang(getHeaderOrDefault(headers, "X-Stainless-Lang", DEFAULT_FINGERPRINT.getStainlessLang()));
        fp.setStainlessPackageVersion(getHeaderOrDefault(headers, "X-Stainless-Package-Version", DEFAULT_FINGERPRINT.getStainlessPackageVersion()));
        fp.setStainlessOS(getHeaderOrDefault(headers, "X-Stainless-OS", DEFAULT_FINGERPRINT.getStainlessOS()));
        fp.setStainlessArch(getHeaderOrDefault(headers, "X-Stainless-Arch", DEFAULT_FINGERPRINT.getStainlessArch()));
        fp.setStainlessRuntime(getHeaderOrDefault(headers, "X-Stainless-Runtime", DEFAULT_FINGERPRINT.getStainlessRuntime()));
        fp.setStainlessRuntimeVersion(getHeaderOrDefault(headers, "X-Stainless-Runtime-Version", DEFAULT_FINGERPRINT.getStainlessRuntimeVersion()));

        return fp;
    }

    /**
     * 合并请求头到已有指纹（保留缓存值）
     */
    private void mergeHeadersIntoFingerprint(Fingerprint fp, java.util.Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return;
        }

        // 仅更新请求中实际携带的字段
        String userAgent = headers.get("User-Agent");
        if (userAgent != null && !userAgent.isEmpty()) {
            fp.setUserAgent(userAgent);
        }
        String lang = headers.get("X-Stainless-Lang");
        if (lang != null && !lang.isEmpty()) {
            fp.setStainlessLang(lang);
        }
        String pkgVersion = headers.get("X-Stainless-Package-Version");
        if (pkgVersion != null && !pkgVersion.isEmpty()) {
            fp.setStainlessPackageVersion(pkgVersion);
        }
        String os = headers.get("X-Stainless-OS");
        if (os != null && !os.isEmpty()) {
            fp.setStainlessOS(os);
        }
        String arch = headers.get("X-Stainless-Arch");
        if (arch != null && !arch.isEmpty()) {
            fp.setStainlessArch(arch);
        }
        String runtime = headers.get("X-Stainless-Runtime");
        if (runtime != null && !runtime.isEmpty()) {
            fp.setStainlessRuntime(runtime);
        }
        String runtimeVersion = headers.get("X-Stainless-Runtime-Version");
        if (runtimeVersion != null && !runtimeVersion.isEmpty()) {
            fp.setStainlessRuntimeVersion(runtimeVersion);
        }
    }

    /**
     * 生成随机 ClientID
     */
    private String generateClientId() {
        byte[] bytes = new byte[16];
        new java.security.SecureRandom().nextBytes(bytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * 比较版本号，判断 clientUA 是否比 cachedUA 更新
     */
    private boolean isNewerVersion(String clientUA, String cachedUA) {
        if (clientUA == null || cachedUA == null) {
            return false;
        }

        // 提取版本号: "claude-cli/x.y.z" -> [x, y, z]
        int[] clientVersion = extractVersion(clientUA);
        int[] cachedVersion = extractVersion(cachedUA);

        if (clientVersion == null || cachedVersion == null) {
            return false;
        }

        // 比较主版本
        if (clientVersion[0] > cachedVersion[0]) {
            return true;
        }
        if (clientVersion[0] < cachedVersion[0]) {
            return false;
        }

        // 比较次版本
        if (clientVersion[1] > cachedVersion[1]) {
            return true;
        }
        if (clientVersion[1] < cachedVersion[1]) {
            return false;
        }

        // 比较补丁版本
        return clientVersion[2] > cachedVersion[2];
    }

    /**
     * 从 User-Agent 字符串提取版本号
     */
    private int[] extractVersion(String userAgent) {
        try {
            // 查找 "/" 后的版本号部分
            int slashIndex = userAgent.lastIndexOf('/');
            if (slashIndex < 0 || slashIndex >= userAgent.length() - 1) {
                return null;
            }

            String versionPart = userAgent.substring(slashIndex + 1);
            // 解析 "x.y.z" 或 "x.y.z-something"
            String[] parts = versionPart.split("[.\\-]");
            if (parts.length < 3) {
                return null;
            }

            int[] version = new int[3];
            version[0] = Integer.parseInt(parts[0]);
            version[1] = Integer.parseInt(parts[1]);
            version[2] = Integer.parseInt(parts[2]);
            return version;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 获取 header 值，如果不存在则返回默认值
     */
    private String getHeaderOrDefault(java.util.Map<String, String> headers, String key, String defaultValue) {
        String value = headers.get(key);
        return value != null && !value.isEmpty() ? value : defaultValue;
    }
}
