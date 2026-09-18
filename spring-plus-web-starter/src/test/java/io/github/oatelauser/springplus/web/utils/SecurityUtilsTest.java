package io.github.oatelauser.springplus.web.utils;

import io.github.oatelauser.springplus.web.stream.NdjsonStreamWriter;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 安全行为回归：NDJSON 单行约束（V17）。
 * <p>
 * FileResources 路径穿越防护（V06）已随 utils 归位迁至 spring-plus-boot-starter
 * （{@code boot.utils.FileResourcesTest}）。
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

}
