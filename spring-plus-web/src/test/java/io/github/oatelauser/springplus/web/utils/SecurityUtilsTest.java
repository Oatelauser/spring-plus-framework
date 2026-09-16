package io.github.oatelauser.springplus.web.utils;

import io.github.oatelauser.springplus.web.stream.NdjsonStreamWriter;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 安全行为回归：NDJSON 单行约束（V17）与 FileResources 路径穿越防护（V06）。
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2026-09-16
 * @since 1.0.1
 */
class SecurityUtilsTest {

    // ───────────── V17：NDJSON writeRawLine 换行 fail-fast ─────────────

    @Test
    void rawLineWithNewlineRejected() throws IOException {
        NdjsonStreamWriter writer = new NdjsonStreamWriter(
                new java.io.ByteArrayOutputStream(), JsonUtils.shared());
        assertThrows(IllegalArgumentException.class, () -> writer.writeRawLine("a\nb"));
        assertThrows(IllegalArgumentException.class, () -> writer.writeRawLine("a\rb"));
        assertDoesNotThrow(() -> writer.writeRawLine("{\"ok\":1}"));
    }

    // ───────────── V06：FileResources 路径穿越 ─────────────

    @Test
    void traversalRejected() {
        assertThrows(IllegalArgumentException.class, () -> FileResources.getResource("../secret.txt"));
        assertThrows(IllegalArgumentException.class, () -> FileResources.getResource("a/../../etc/passwd"));
        assertThrows(IllegalArgumentException.class, () -> FileResources.getResource("/etc/passwd"));
        assertThrows(IllegalArgumentException.class, () -> FileResources.getResource("C:/windows/win.ini"));
        assertThrows(IllegalArgumentException.class, () -> FileResources.getResource("D:\\secret"));
        assertThrows(IllegalArgumentException.class, () -> FileResources.getResource("  "));
    }

    @Test
    void legitimateRelativePathsStillPass() {
        assertDoesNotThrow(() -> FileResources.getResource("config/app.yml"));
        assertDoesNotThrow(() -> FileResources.getResource("lua\\bget.lua"));
    }

}
