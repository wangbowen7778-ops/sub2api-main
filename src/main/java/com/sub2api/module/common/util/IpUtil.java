package com.sub2api.module.common.util;

import cn.hutool.crypto.digest.DigestUtil;
import jakarta.servlet.http.HttpServletRequest;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * IP 工具类
 *
 * @author Alibaba Java Code Guidelines
 */
public class IpUtil {

    private static final String UNKNOWN = "unknown";
    private static final String LOCALHOST_IP = "127.0.0.1";
    private static final String LOCALHOST_IPV6 = "0:0:0:0:0:0:0:1";
    private static final int IP_LENGTH = 15;

    /**
     * 获取客户端真实 IP
     */
    public static String getRealIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Real-IP");
        if (ip == null || UNKNOWN.equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Forwarded-For");
        }
        if (ip == null || UNKNOWN.equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || UNKNOWN.equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || UNKNOWN.equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_CLIENT_IP");
        }
        if (ip == null || UNKNOWN.equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_X_FORWARDED_FOR");
        }
        if (ip == null || UNKNOWN.equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
            if (LOCALHOST_IP.equals(ip) || LOCALHOST_IPV6.equals(ip)) {
                try {
                    InetAddress inetAddress = InetAddress.getLocalHost();
                    ip = inetAddress.getHostAddress();
                } catch (UnknownHostException e) {
                    // ignore
                }
            }
        }
        // 多个代理的情况，取第一个 IP
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        if (LOCALHOST_IPV6.equals(ip)) {
            ip = LOCALHOST_IP;
        }
        return ip;
    }

    /**
     * 生成 IP 对应的 Key
     */
    public static String generateIpKey(String ip) {
        return DigestUtil.md5Hex(ip);
    }

    /**
     * 校验 IP 格式
     */
    public static boolean isValidIp(String ip) {
        if (ip == null || ip.isEmpty()) {
            return false;
        }
        // 支持 IPv4 和 IPv6
        return ip.matches("^((25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.){3}(25[0-5]|2[0-4]\\d|[01]?\\d\\d?)$")
                || ip.matches("^([0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}$")
                || ip.matches("^::$")
                || ip.matches("^::1$");
    }
}
