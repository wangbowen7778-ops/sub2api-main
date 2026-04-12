package com.sub2api.module.gateway.service;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Claude Code 检测验证器
 * 用于检测请求是否来自 Claude Code CLI 客户端
 * 参考 Go 版本 claude_code_validator.go 实现
 */
@Slf4j
public class ClaudeCodeValidator {

    /**
     * User-Agent 匹配: claude-cli/x.x.x (仅支持官方 CLI，大小写不敏感)
     */
    private static final Pattern CLAUDE_CODE_UA_PATTERN = Pattern.compile("(?i)^claude-cli/\\d+\\.\\d+\\.\\d+");

    /**
     * 带捕获组的版本提取正则
     */
    private static final Pattern CLAUDE_CODE_UA_VERSION_PATTERN = Pattern.compile("(?i)^claude-cli/(\\d+\\.\\d+\\.\\d+)");

    /**
     * System prompt 相似度阈值
     */
    private static final double SYSTEM_PROMPT_THRESHOLD = 0.5;

    /**
     * Claude Code 官方 System Prompt 模板
     */
    private static final String[] CLAUDE_CODE_SYSTEM_PROMPTS = {
            "You are Claude Code, Anthropic's official CLI for Claude.",
            "You are a Claude agent, built on Anthropic's Claude Agent SDK.",
            "You are Claude Code, Anthropic's official CLI for Claude, running within the Claude Agent SDK.",
            "You are a file search specialist for Claude Code, Anthropic's official CLI for Claude.",
            "You are a helpful AI assistant tasked with summarizing conversations.",
            "You are an interactive CLI tool that helps users"
    };

    /**
     * 验证请求是否来自 Claude Code CLI
     *
     * @param request HTTP 请求
     * @param body    请求体
     * @return true 如果请求来自 Claude Code CLI
     */
    public boolean validate(HttpServletRequest request, Map<String, Object> body) {
        // Step 1: User-Agent 检查
        String ua = request.getHeader("User-Agent");
        if (!isValidClaudeCodeUA(ua)) {
            return false;
        }

        // Step 2: 非 messages 路径，只要 UA 匹配就通过
        String path = request.getRequestURI();
        if (!path.contains("messages")) {
            return true;
        }

        // Step 3: 检查 max_tokens=1 + haiku 探测请求绕过
        // 这类请求用于 Claude Code 验证 API 连通性，不携带 system prompt
        if (isMaxTokensOneHaikuRequest(request)) {
            return true;
        }

        // Step 4: messages 路径，进行严格验证

        // 4.1 检查 system prompt 相似度
        if (!hasClaudeCodeSystemPrompt(body)) {
            return false;
        }

        // 4.2 检查必需的 headers
        String xApp = request.getHeader("X-App");
        if (xApp == null || xApp.isEmpty()) {
            return false;
        }

        String anthropicBeta = request.getHeader("anthropic-beta");
        if (anthropicBeta == null || anthropicBeta.isEmpty()) {
            return false;
        }

        String anthropicVersion = request.getHeader("anthropic-version");
        if (anthropicVersion == null || anthropicVersion.isEmpty()) {
            return false;
        }

        // 4.3 验证 metadata.user_id
        if (body == null) {
            return false;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) body.get("metadata");
        if (metadata == null) {
            return false;
        }

        Object userIdObj = metadata.get("user_id");
        if (!(userIdObj instanceof String) || ((String) userIdObj).isEmpty()) {
            return false;
        }

        String userId = (String) userIdObj;
        if (!isValidMetadataUserId(userId)) {
            return false;
        }

        return true;
    }

    /**
     * 仅验证 User-Agent（用于不需要解析请求体的场景）
     *
     * @param ua User-Agent 字符串
     * @return true 如果是 Claude Code CLI 的 UA
     */
    public boolean validateUserAgent(String ua) {
        return isValidClaudeCodeUA(ua);
    }

    /**
     * 检查请求是否包含 Claude Code 系统提示词
     * 使用 Dice coefficient 算法进行相似度匹配
     *
     * @param body 请求体
     * @return true 如果包含 Claude Code 系统提示词
     */
    public boolean includesClaudeCodeSystemPrompt(Map<String, Object> body) {
        return hasClaudeCodeSystemPrompt(body);
    }

    /**
     * 从 User-Agent 中提取 Claude Code 版本号
     *
     * @param ua User-Agent 字符串
     * @return 版本号，如 "2.1.22"，如果不符合格式返回空字符串
     */
    public String extractVersion(String ua) {
        if (ua == null) {
            return "";
        }
        Matcher matcher = CLAUDE_CODE_UA_VERSION_PATTERN.matcher(ua);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }

    /**
     * 检查 User-Agent 是否是有效的 Claude Code CLI UA
     */
    private boolean isValidClaudeCodeUA(String ua) {
        if (ua == null) {
            return false;
        }
        return CLAUDE_CODE_UA_PATTERN.matcher(ua).find();
    }

    /**
     * 检查是否是 max_tokens=1 + haiku 探测请求
     * 通过检查请求头 Is-Max-Tokens-One-Haiku
     */
    private boolean isMaxTokensOneHaikuRequest(HttpServletRequest request) {
        // 检查自定义请求头
        String header = request.getHeader("Is-Max-Tokens-One-Haiku");
        return "true".equalsIgnoreCase(header);
    }

    /**
     * 检查请求体是否包含 Claude Code 系统提示词
     */
    @SuppressWarnings("unchecked")
    private boolean hasClaudeCodeSystemPrompt(Map<String, Object> body) {
        if (body == null) {
            return false;
        }

        // 检查 model 字段
        if (!(body.get("model") instanceof String)) {
            return false;
        }

        // 获取 system 字段
        Object systemObj = body.get("system");
        if (systemObj == null) {
            return false;
        }

        if (systemObj instanceof String) {
            // system 是字符串形式
            String systemText = (String) systemObj;
            if (!systemText.isEmpty() && bestSimilarityScore(systemText) >= SYSTEM_PROMPT_THRESHOLD) {
                return true;
            }
        } else if (systemObj instanceof java.util.List) {
            // system 是列表形式
            java.util.List<Object> systemEntries = (java.util.List<Object>) systemObj;
            for (Object entry : systemEntries) {
                if (!(entry instanceof Map)) {
                    continue;
                }
                Map<String, Object> entryMap = (Map<String, Object>) entry;
                Object textObj = entryMap.get("text");
                if (!(textObj instanceof String)) {
                    continue;
                }
                String text = (String) textObj;
                if (!text.isEmpty() && bestSimilarityScore(text) >= SYSTEM_PROMPT_THRESHOLD) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * 计算文本与所有 Claude Code 模板的最佳相似度
     */
    private double bestSimilarityScore(String text) {
        String normalizedText = normalizePrompt(text);
        double bestScore = 0.0;

        for (String template : CLAUDE_CODE_SYSTEM_PROMPTS) {
            String normalizedTemplate = normalizePrompt(template);
            double score = diceCoefficient(normalizedText, normalizedTemplate);
            if (score > bestScore) {
                bestScore = score;
            }
        }

        return bestScore;
    }

    /**
     * 标准化提示词文本（去除多余空白）
     */
    private String normalizePrompt(String text) {
        // 将所有空白字符替换为单个空格，并去除首尾空白
        return text.replaceAll("\\s+", " ").trim();
    }

    /**
     * 计算两个字符串的 Dice 系数（Sørensen–Dice coefficient）
     * 这是 string-similarity 库使用的算法
     * 公式: 2 * |intersection| / (|bigrams(a)| + |bigrams(b)|)
     */
    private double diceCoefficient(String a, String b) {
        if (a.equals(b)) {
            return 1.0;
        }

        if (a.length() < 2 || b.length() < 2) {
            return 0.0;
        }

        // 生成 bigrams
        Map<String, Integer> bigramsA = getBigrams(a);
        Map<String, Integer> bigramsB = getBigrams(b);

        if (bigramsA.isEmpty() || bigramsB.isEmpty()) {
            return 0.0;
        }

        // 计算交集大小
        int intersection = 0;
        for (Map.Entry<String, Integer> entry : bigramsA.entrySet()) {
            String bigram = entry.getKey();
            int countA = entry.getValue();
            if (bigramsB.containsKey(bigram)) {
                int countB = bigramsB.get(bigram);
                intersection += Math.min(countA, countB);
            }
        }

        // 计算总 bigram 数量
        int totalA = 0;
        for (int count : bigramsA.values()) {
            totalA += count;
        }
        int totalB = 0;
        for (int count : bigramsB.values()) {
            totalB += count;
        }

        return (2.0 * intersection) / (totalA + totalB);
    }

    /**
     * 获取字符串的所有 bigrams（相邻字符对）
     */
    private Map<String, Integer> getBigrams(String s) {
        Map<String, Integer> bigrams = new java.util.HashMap<>();
        String lowerS = s.toLowerCase();

        for (int i = 0; i < lowerS.length() - 1; i++) {
            String bigram = lowerS.substring(i, i + 2);
            bigrams.merge(bigram, 1, Integer::sum);
        }

        return bigrams;
    }

    /**
     * 验证 metadata.user_id 格式
     * 格式: base64(hex(session_id)):base64(version)
     */
    private boolean isValidMetadataUserId(String userId) {
        if (userId == null || userId.isEmpty()) {
            return false;
        }

        // 检查是否包含冒号分隔符
        if (!userId.contains(":")) {
            return false;
        }

        String[] parts = userId.split(":", 2);
        if (parts.length != 2) {
            return false;
        }

        String sessionIdPart = parts[0];
        String versionPart = parts[1];

        // 检查 session ID 部分（应该是 base64 编码的 hex）
        if (sessionIdPart.isEmpty()) {
            return false;
        }

        // 检查 version 部分（应该是数字或 v数字格式）
        if (versionPart.isEmpty()) {
            return false;
        }

        // version 应该是纯数字或 v开头的数字
        return versionPart.matches("\\d+") || versionPart.matches("v\\d+");
    }

    /**
     * 比较两个 semver 版本号
     *
     * @return -1 (a < b), 0 (a == b), 1 (a > b)
     */
    public int compareVersions(String a, String b) {
        int[] partsA = parseSemver(a);
        int[] partsB = parseSemver(b);

        for (int i = 0; i < 3; i++) {
            if (partsA[i] < partsB[i]) {
                return -1;
            }
            if (partsA[i] > partsB[i]) {
                return 1;
            }
        }
        return 0;
    }

    /**
     * 解析 semver 版本号为 [major, minor, patch]
     */
    private int[] parseSemver(String v) {
        int[] result = {0, 0, 0};

        if (v.startsWith("v")) {
            v = v.substring(1);
        }

        String[] parts = v.split("\\.");
        for (int i = 0; i < Math.min(parts.length, 3); i++) {
            try {
                result[i] = Integer.parseInt(parts[i]);
            } catch (NumberFormatException e) {
                // 忽略解析错误
            }
        }

        return result;
    }
}
