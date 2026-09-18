package io.github.oatelauser.springplus.boot.utils;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 日志脱敏工具（V10/V21 / CWE-532、CWE-117）。
 * <ul>
 *   <li>{@link #maskSensitiveValues(String)}：JSON 与 form-urlencoded 载荷中敏感键的值替换为 {@code ***}
 *       （默认键：password/passwd/token/secret/authorization/phone/mobile/idCard，子串不区分大小写匹配键名）</li>
 *   <li>{@link #sanitizeLine(String)}：换行折叠为空格（防日志伪造）+ 超长截断（默认 512 字符）</li>
 * </ul>
 * 面向日志旁录/请求追踪/HTTP 客户端 BODY 级日志；不做结构化解析（正则形态匹配），
 * 覆盖常见 JSON/form 文本，异常输入原样返回不抛错。
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2026-09-17
 * @since 1.1.0
 */
public final class LogSanitizer {

    /** 默认敏感键（子串匹配，不区分大小写） */
    public static final Set<String> DEFAULT_SENSITIVE_KEYS = Set.of(
            "password", "passwd", "token", "secret", "authorization", "phone", "mobile", "idcard");

    private static final int MAX_LINE_LENGTH = 512;

    private static final Pattern JSON_SENSITIVE =
            Pattern.compile("(\"[^\"]*?(" + String.join("|", DEFAULT_SENSITIVE_KEYS) + ")[^\"]*?\"\\s*:\\s*\")([^\"]*)(\")",
                    Pattern.CASE_INSENSITIVE);

    private static final Pattern FORM_SENSITIVE =
            Pattern.compile("((?:^|&)(" + String.join("|", DEFAULT_SENSITIVE_KEYS) + ")=)([^&]*)",
                    Pattern.CASE_INSENSITIVE);

    private LogSanitizer() {
    }

    /**
     * 掩码载荷中敏感键的值（JSON 字符串值与 form 值）；非敏感内容原样保留。
     */
    public static String maskSensitiveValues(String payload) {
        if (payload == null || payload.isEmpty()) {
            return payload;
        }
        String masked = JSON_SENSITIVE.matcher(payload).replaceAll("$1***$4");
        return FORM_SENSITIVE.matcher(masked).replaceAll("$1***");
    }

    /**
     * 单行化（CRLF 折叠为空格，防日志行伪造）+ 截断到 {@value #MAX_LINE_LENGTH} 字符。
     */
    public static String sanitizeLine(String value) {
        if (value == null) {
            return null;
        }
        String flat = value.replace("\r\n", " ").replace('\n', ' ').replace('\r', ' ').trim();
        return flat.length() <= MAX_LINE_LENGTH ? flat : flat.substring(0, MAX_LINE_LENGTH) + "...(truncated)";
    }

    /** 键名是否命中默认敏感键集（供调用方做键级判断） */
    public static boolean isSensitiveKey(String key) {
        if (key == null) {
            return false;
        }
        String normalized = key.toLowerCase(Locale.ROOT);
        return DEFAULT_SENSITIVE_KEYS.stream().anyMatch(normalized::contains);
    }

}
