package io.github.oatelauser.springplus.web.stream;

import io.github.oatelauser.springplus.web.utils.JsonUtils;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * SSE 响应拆分防护（CWE-113）：event/id 含换行符必须 fail-fast，禁止写出可伪造帧。
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2026-09-16
 * @since 1.0.1
 */
class SseStreamWriterSecurityTest {

    private SseStreamWriter writer() {
        return new SseStreamWriter(new java.io.ByteArrayOutputStream(), JsonUtils.shared());
    }

    @Test
    void eventWithNewlineRejected() {
        Map<String, Object> data = Map.of("k", "v");
        assertThrows(IllegalArgumentException.class, () -> writer().writeEvent("evil\nevent: app-error", data));
        assertThrows(IllegalArgumentException.class, () -> writer().writeEvent("evil\r\ndata: x", data));
    }

    @Test
    void idWithNewlineRejected() {
        Map<String, Object> data = Map.of("k", "v");
        assertThrows(IllegalArgumentException.class, () -> writer().writeEvent("1\nid: 2", "tick", data));
        assertThrows(IllegalArgumentException.class, () -> writer().writeEvent("1\r", "tick", data));
    }

    @Test
    void legitimateSingleLineValuesStillPass() {
        assertDoesNotThrow(() -> writer().writeEvent("app-error", Map.of("code", "B0101")));
    }

}
