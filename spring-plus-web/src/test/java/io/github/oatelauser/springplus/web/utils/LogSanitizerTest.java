package io.github.oatelauser.springplus.web.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V10/V21：日志脱敏——JSON/form 敏感键掩码、CRLF 单行化、超长截断。
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2026-09-17
 * @since 1.1.0
 */
class LogSanitizerTest {

    // ───────────── V10：JSON 掩码 ─────────────

    @Test
    void jsonSensitiveValuesMasked() {
        String body = "{\"username\":\"alice\",\"password\":\"p@ss\",\"token\":\"abc123\",\"nested\":{\"phone\":\"13800000000\"}}";
        String masked = LogSanitizer.maskSensitiveValues(body);
        assertFalse(masked.contains("p@ss"));
        assertFalse(masked.contains("abc123"));
        assertFalse(masked.contains("13800000000"));
        assertTrue(masked.contains("\"username\":\"alice\""), "非敏感键原样保留");
        assertEquals(3, masked.split("\\*\\*\\*", -1).length - 1, "恰好三处掩码");
    }

    @Test
    void jsonKeyNameSubstringMatched() {
        String masked = LogSanitizer.maskSensitiveValues("{\"oldPassword\":\"x1\",\"user_token\":\"x2\"}");
        assertFalse(masked.contains("x1"));
        assertFalse(masked.contains("x2"));
    }

    @Test
    void plainJsonWithoutSensitiveKeysUntouched() {
        String body = "{\"name\":\"alice\",\"age\":18}";
        assertEquals(body, LogSanitizer.maskSensitiveValues(body));
    }

    // ───────────── V10：form 掩码 ─────────────

    @Test
    void formValuesMasked() {
        String masked = LogSanitizer.maskSensitiveValues("username=alice&password=secret1&mobile=13800000000");
        assertFalse(masked.contains("secret1"));
        assertFalse(masked.contains("13800000000"));
        assertTrue(masked.contains("username=alice"));
    }

    // ───────────── V21：单行化与截断 ─────────────

    @Test
    void crlfFoldedToSpaces() {
        assertEquals("fake WARN entry", LogSanitizer.sanitizeLine("fake\r\nWARN entry"));
        assertEquals("a b c", LogSanitizer.sanitizeLine("a\nb\rc"));
    }

    @Test
    void oversizedValueTruncated() {
        String long_ = "x".repeat(600);
        String sanitized = LogSanitizer.sanitizeLine(long_);
        assertTrue(sanitized.startsWith("xxx"));
        assertTrue(sanitized.endsWith("...(truncated)"));
        assertTrue(sanitized.length() < 600);
    }

    @Test
    void nullPassthrough() {
        assertEquals(null, LogSanitizer.sanitizeLine(null));
        assertEquals(null, LogSanitizer.maskSensitiveValues(null));
    }

    @Test
    void sensitiveKeyDetection() {
        assertTrue(LogSanitizer.isSensitiveKey("PASSWORD"));
        assertTrue(LogSanitizer.isSensitiveKey("userMobile"));
        assertFalse(LogSanitizer.isSensitiveKey("username"));
        assertFalse(LogSanitizer.isSensitiveKey(null));
    }

}
