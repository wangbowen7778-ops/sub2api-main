package com.sub2api.module.account.model.enums;

import lombok.Getter;

/**
 * AI 平台枚举
 *
 * @author Alibaba Java Code Guidelines
 */
@Getter
public enum Platform {

    ANTHROPIC("anthropic", "Anthropic", "https://api.anthropic.com"),
    OPENAI("openai", "OpenAI", "https://api.openai.com"),
    GOOGLE("google", "Google AI", "https://generativelanguage.googleapis.com"),
    GEMINI("gemini", "Gemini", "https://generativelanguage.googleapis.com"),
    ANTIGRAVITY("antigravity", "Antigravity", "https://api.antigravity.dev"),
    CLAUDE("claude", "Claude (Anthropic)", "https://api.anthropic.com");

    private final String id;
    private final String displayName;
    private final String baseUrl;

    Platform(String id, String displayName, String baseUrl) {
        this.id = id;
        this.displayName = displayName;
        this.baseUrl = baseUrl;
    }

    public static Platform fromId(String id) {
        for (Platform platform : values()) {
            if (platform.id.equalsIgnoreCase(id)) {
                return platform;
            }
        }
        return null;
    }

    public static Platform fromHost(String host) {
        if (host == null) return null;
        host = host.toLowerCase();
        if (host.contains("anthropic") || host.contains("claude")) {
            return ANTHROPIC;
        }
        if (host.contains("openai")) {
            return OPENAI;
        }
        if (host.contains("google") || host.contains("gemini")) {
            return GOOGLE;
        }
        if (host.contains("antigravity")) {
            return ANTIGRAVITY;
        }
        return null;
    }
}
