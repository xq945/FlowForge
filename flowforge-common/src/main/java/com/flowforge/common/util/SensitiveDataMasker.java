package com.flowforge.common.util;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 敏感数据脱敏。
 *
 * <p>用于日志、执行尝试记录、消息载荷等「需要留痕但不能留原文」的场景。
 * 这里做的是**尽力而为的兜底**：真正敏感的数据不应进入这些通道，
 * 脱敏只是防止误操作留下明文。
 */
public final class SensitiveDataMasker {

    private static final String MASK = "***";

    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "password", "passwd", "pwd", "secret", "clientsecret", "client_secret",
            "token", "accesstoken", "access_token", "refreshtoken", "refresh_token",
            "authorization", "cookie", "set-cookie", "sessionid", "session_id",
            "apikey", "api_key", "privatekey", "private_key", "credential", "credentials"
    );

    /**
     * 匹配三种形态：URL 查询串 {@code key=value}、JSON {@code "key":"value"}、请求头 {@code Key: value}。
     * 值允许包含空格（如 {@code Authorization: Bearer xxx}），因此只在 {@code & , ; }} 处截断。
     */
    private static final Pattern KV_PATTERN = Pattern.compile(
            "(?i)(\"?)([a-z0-9_]*(?:password|passwd|pwd|secret|token|credential|apikey|api_key)[a-z0-9_]*)(\"?\\s*[=:]\\s*)"
                    + "(\"[^\"]*\"|[^&,;}]+)");

    private SensitiveDataMasker() {
    }

    /** 脱敏 URL 查询串中的敏感参数，保留参数名便于排查。 */
    public static String maskUrl(String url) {
        return maskText(url);
    }

    /**
     * 脱敏 JSON / 表单文本中的敏感字段。
     *
     * <p>原值带引号（JSON）时打码值也带引号，保证输出仍是合法的 JSON；
     * 表单或查询串则输出裸的 {@code ***}。
     */
    public static String maskText(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        return KV_PATTERN.matcher(text).replaceAll(match -> {
            String maskedValue = match.group(4).startsWith("\"") ? "\"" + MASK + "\"" : MASK;
            return match.group(1) + match.group(2) + match.group(3) + maskedValue;
        });
    }

    /** 请求/响应头的安全取值：敏感头整体打码。 */
    public static String maskHeader(String name, String value) {
        if (name == null || value == null) {
            return value;
        }
        return SENSITIVE_KEYS.contains(name.toLowerCase(Locale.ROOT)) ? MASK : value;
    }

    /** 截断过长内容，避免日志与尝试记录被大报文撑爆。 */
    public static String truncate(String text, int maxLength) {
        if (text == null) {
            return null;
        }
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...(truncated)";
    }

    /** 一次性处理：先脱敏再截断，顺序不能反。 */
    public static String maskAndTruncate(String text, int maxLength) {
        return truncate(maskText(text), maxLength);
    }
}
