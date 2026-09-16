package io.github.oatelauser.springplus.web.error.output;

import io.github.oatelauser.springplus.web.error.descriptor.ErrorDescriptor;
import io.github.oatelauser.springplus.web.error.descriptor.ExceptionContext;
import io.github.oatelauser.springplus.web.error.descriptor.OutputProtocol;
import io.github.oatelauser.springplus.web.response.SystemStatus;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link NdjsonExceptionProcessor} 单元测试：两态渲染（未提交 → 状态码+错误行；已提交 → 追加错误行）。
 * <p>
 * v3.0：状态码经协议无关的 {@code statusIntent} 携带（不再借道 JsonErrorHint），错误行格式经
 * {@code bodyCustomizer} 定制（更名自 outputBase）。
 *
 * @author Oatelauser
 * @date 2026-08-21
 * @since 2.1
 */
class NdjsonExceptionProcessorTest {

    private final NdjsonExceptionProcessor processor = new NdjsonExceptionProcessor(null);

    private ErrorDescriptor descriptor() {
        return ErrorDescriptor.of(SystemStatus.INTERNAL_ERROR, new IllegalStateException("boom")).build();
    }

    @Test
    void declaresNdjsonProtocolAndCommittedSupport() {
        assertEquals(OutputProtocol.NDJSON, processor.protocol());
        assertTrue(processor.handlesCommitted(), "NDJSON 逐行协议必须支持已提交响应补写错误行");
    }

    @Test
    void uncommittedWritesSingleErrorLineWithProtocolHeaders() throws IOException {
        MockHttpServletResponse response = new MockHttpServletResponse();
        ErrorDescriptor descriptor = descriptor();

        Object result = processor.handle(descriptor,
                new ExceptionContext(descriptor, null, response, null, OutputProtocol.NDJSON));

        assertNull(result, "直写输出流后返回 null，绕开 MVC 返回值处理器");
        assertEquals(200, response.getStatus(), "缺省 statusIntent = null → 200（业务码承载错误语义）");
        assertTrue(response.getContentType().startsWith("application/x-ndjson"), response.getContentType());
        assertEquals("no", response.getHeader("X-Accel-Buffering"));
        String content = response.getContentAsString(StandardCharsets.UTF_8);
        assertTrue(content.endsWith("\n"), "错误行必须以 \\n 终止: " + content);
        assertEquals(1, content.lines().count(), "错误响应物理上只有一行: " + content);
        assertTrue(content.contains("\"code\":\"" + descriptor.getCode() + "\""), content);
        assertTrue(content.contains("\"success\":false"), content);
    }

    @Test
    void committedAppendsErrorLineToExistingStream() throws IOException {
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        // 模拟业务已写出部分数据行：flushBuffer 使 mock 进入 committed 状态
        response.getOutputStream().write("{\"seq\":0}\n".getBytes(StandardCharsets.UTF_8));
        response.flushBuffer();
        assertTrue(response.isCommitted());
        ErrorDescriptor descriptor = descriptor();

        processor.handle(descriptor,
                new ExceptionContext(descriptor, null, response, null, OutputProtocol.NDJSON));

        String content = response.getContentAsString(StandardCharsets.UTF_8);
        assertEquals(2, content.lines().count(), "已提交流：业务数据行 + 追加的错误行: " + content);
        assertTrue(content.startsWith("{\"seq\":0}\n"), "既有数据行必须原样保留");
        assertTrue(content.endsWith("\n"));
        assertTrue(content.contains("\"code\":\"" + descriptor.getCode() + "\""), content);
    }

    @Test
    void nullResponseIsTolerated() {
        ErrorDescriptor descriptor = descriptor();

        Object result = processor.handle(descriptor,
                new ExceptionContext(descriptor, null, null, null, OutputProtocol.NDJSON));

        assertNull(result, "缺 response 时防御性返回 null 而不是抛错");
    }

    // ─────────────────────────────────────────────────────────────
    // v3.0：自定义错误行（@NdjsonExceptionResponse.output → ErrorDescriptor.bodyCustomizer）
    // ─────────────────────────────────────────────────────────────

    /**
     * 自定义 output 桩：微信 errcode/errmsg 风格（同 user-service 的 WechatStyleOutput 语义）。
     */
    private static final ExceptionBodyCustomizer WECHAT_OUTPUT = ctx -> {
        Map<String, Object> body = new LinkedHashMap<>(2);
        body.put("errcode", -1);
        body.put("errmsg", ctx.message());
        return body;
    };

    /**
     * 炸药包产物：getter 抛错，让序列化在异常处理器内部引爆（坑 5 场景）。
     */
    static class PoisonBean {

        public String getBoom() {
            throw new IllegalStateException("getter 炸了");
        }
    }

    @Test
    void customBodyCustomizerShapesFreshErrorLine() throws IOException {
        MockHttpServletResponse response = new MockHttpServletResponse();
        ErrorDescriptor descriptor = ErrorDescriptor.of("B0001", "签名校验失败", new IllegalStateException("boom"))
                .bodyCustomizer(WECHAT_OUTPUT)
                .statusIntent(HttpStatus.UNAUTHORIZED)
                .build();

        processor.handle(descriptor,
                new ExceptionContext(descriptor, null, response, null, OutputProtocol.NDJSON));

        assertEquals(401, response.getStatus(), "statusIntent 在流未开始路径生效");
        String content = response.getContentAsString(StandardCharsets.UTF_8);
        assertTrue(content.contains("\"errcode\":-1"), "错误行必须是自定义格式: " + content);
        assertTrue(content.contains("\"errmsg\":\"签名校验失败\""), "message 必须来自 descriptor: " + content);
        assertFalse(content.contains("\"success\""), "自定义 output 下不得出现 SimpleResponse 字段: " + content);
        assertEquals(1, content.lines().count(), "错误行物理上单行: " + content);
    }

    @Test
    void customBodyCustomizerShapesCommittedAppendLine() throws IOException {
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getOutputStream().write("{\"seq\":0}\n".getBytes(StandardCharsets.UTF_8));
        response.flushBuffer();
        assertTrue(response.isCommitted());
        ErrorDescriptor descriptor = ErrorDescriptor.of("B0001", "签名校验失败", new IllegalStateException("boom"))
                .bodyCustomizer(WECHAT_OUTPUT)
                .build();

        processor.handle(descriptor,
                new ExceptionContext(descriptor, null, response, null, OutputProtocol.NDJSON));

        String content = response.getContentAsString(StandardCharsets.UTF_8);
        assertEquals(2, content.lines().count(), "数据行 + 追加错误行: " + content);
        assertTrue(content.startsWith("{\"seq\":0}\n"), "既有数据行必须原样保留: " + content);
        assertTrue(content.contains("\"errcode\":-1"), "已提交路径同样走 bodyCustomizer 定制: " + content);
        assertFalse(content.contains("\"code\""), "补写行不得是默认 SimpleResponse 格式: " + content);
    }

    @Test
    void unserializableOutputDegradesToStandardLine() throws IOException {
        MockHttpServletResponse response = new MockHttpServletResponse();
        ExceptionBodyCustomizer poison = ctx -> new PoisonBean();
        ErrorDescriptor descriptor = ErrorDescriptor.of("B0001", "x", new IllegalStateException("boom"))
                .bodyCustomizer(poison)
                .build();

        processor.handle(descriptor,
                new ExceptionContext(descriptor, null, response, null, OutputProtocol.NDJSON));

        String content = response.getContentAsString(StandardCharsets.UTF_8);
        assertTrue(content.contains("\"code\":\"B0001\""), "序列化炸裂必须降级标准错误行: " + content);
        assertTrue(content.contains("\"success\":false"), content);
        assertEquals(1, content.lines().count(), "降级行仍是合法单行 NDJSON: " + content);
    }

    @Test
    void nullTransformFallsBackToStandardLine() throws IOException {
        MockHttpServletResponse response = new MockHttpServletResponse();
        ErrorDescriptor descriptor = ErrorDescriptor.of("B0001", "x", new IllegalStateException("boom"))
                .bodyCustomizer(ctx -> null)
                .build();

        processor.handle(descriptor,
                new ExceptionContext(descriptor, null, response, null, OutputProtocol.NDJSON));

        String content = response.getContentAsString(StandardCharsets.UTF_8);
        assertTrue(content.contains("\"code\":\"B0001\""), "transform 返回 null 回落默认 SimpleResponse 行: " + content);
        assertEquals(1, content.lines().count(), content);
    }

}
