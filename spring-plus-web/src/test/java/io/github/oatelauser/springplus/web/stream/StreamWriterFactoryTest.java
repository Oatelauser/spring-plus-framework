package io.github.oatelauser.springplus.web.stream;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link HttpWriterFactory} 单元测试：各工厂方法的协议头策略（见工厂类 javadoc 头部策略表）。
 * <p>
 * 用 {@link MockHttpServletResponse} 断言头与 bufferSize，避免起容器。
 *
 * @author Oatelauser
 * @date 2026-08-21
 * @since 1.0
 */
class StreamWriterFactoryTest {

    private final HttpWriterFactory factory = new HttpWriterFactory(null);

    @Test
    void sseConfiguresEventStreamHeaders() throws IOException {
        MockHttpServletResponse response = new MockHttpServletResponse();

        SseStreamWriter writer = factory.sse(response);

        assertInstanceOf(SseStreamWriter.class, writer);
        // getContentType() 会带上已设置的字符集（与真实容器行为一致）
        assertTrue(response.getContentType().startsWith("text/event-stream"), response.getContentType());
        assertEquals("UTF-8", response.getCharacterEncoding());
        assertEquals("no-cache", response.getHeader(HttpHeaders.CACHE_CONTROL));
        assertEquals("no", response.getHeader("X-Accel-Buffering"));
        assertEquals(0, response.getBufferSize(), "SSE 必须关闭容器缓冲保证逐条即时送达");
    }

    @Test
    void ndjsonConfiguresLineStreamHeaders() throws IOException {
        MockHttpServletResponse response = new MockHttpServletResponse();

        NdjsonStreamWriter writer = factory.ndjson(response);

        assertInstanceOf(NdjsonStreamWriter.class, writer);
        assertTrue(response.getContentType().startsWith("application/x-ndjson"), response.getContentType());
        assertEquals("UTF-8", response.getCharacterEncoding());
        assertNull(response.getHeader(HttpHeaders.CACHE_CONTROL), "NDJSON 不需要 no-cache（非事件流语义）");
        assertEquals("no", response.getHeader("X-Accel-Buffering"));
        assertEquals(0, response.getBufferSize());
    }

    @Test
    void chunkLeavesContentTypeToCaller() throws IOException {
        MockHttpServletResponse response = new MockHttpServletResponse();

        ChunkStreamWriter writer = factory.chunk(response);

        assertInstanceOf(ChunkStreamWriter.class, writer);
        assertNull(response.getContentType(), "chunk 载体泛化（文本/二进制皆可），Content-Type 必须留给调用方");
        assertEquals(0, response.getBufferSize());
    }

    @Test
    void downloadInfersTypeAndEncodesChineseFilename() throws IOException {
        MockHttpServletResponse response = new MockHttpServletResponse();

        FileDownloadWriter writer = factory.download(response, "报表.csv");

        assertInstanceOf(FileDownloadWriter.class, writer);
        assertTrue(response.getContentType().startsWith("text/csv"), "按扩展名 .csv 推断: " + response.getContentType());
        String disposition = response.getHeader(HttpHeaders.CONTENT_DISPOSITION);
        assertTrue(disposition != null && disposition.startsWith("attachment"), "缺省 attachment 下载语义: " + disposition);
        // ASCII 兜底名：两个中文字符替换为下划线
        assertTrue(disposition.contains("filename=\"__.csv\""), "老浏览器兜底名: " + disposition);
        // RFC 5987 扩展名：报=%E6%8A%A5 表=%E8%A1%A8
        assertTrue(disposition.contains("filename*=UTF-8''%E6%8A%A5%E8%A1%A8.csv"), disposition);
    }

    @Test
    void downloadInlineUsesInlineDisposition() throws IOException {
        MockHttpServletResponse response = new MockHttpServletResponse();

        factory.download(response, "preview.pdf", true);

        assertTrue(response.getContentType().startsWith("application/pdf"), response.getContentType());
        String disposition = response.getHeader(HttpHeaders.CONTENT_DISPOSITION);
        assertTrue(disposition != null && disposition.startsWith("inline"),
                "inline 语义供浏览器内直接打开预览: " + disposition);
    }

    @Test
    void downloadUnknownExtensionFallsBackToOctetStream() throws IOException {
        MockHttpServletResponse response = new MockHttpServletResponse();

        factory.download(response, "数据文件");

        assertTrue(response.getContentType().startsWith("application/octet-stream"),
                "无扩展名兜底通用二进制流: " + response.getContentType());
    }

    // ─────────────────────────────────────────────────────────────
    // v2.1 converter 版工厂：注入 MVC 适配器后走消息转换器矩阵
    // ─────────────────────────────────────────────────────────────

    /**
     * Bean 装配路径的工厂行为：ndjson / chunk 的 Object 序列化按 converter 匹配。
     */
    @Test
    void adapterBasedFactorySerializesNdjsonViaConverter() throws IOException {
        HttpWriterFactory adapterFactory = new HttpWriterFactory(null, adapter());
        MockHttpServletResponse response = new MockHttpServletResponse();

        NdjsonStreamWriter writer = adapterFactory.ndjson(response);
        writer.writeLine(Map.of("seq", 0, "token", "delta-0"));

        String content = response.getContentAsString(StandardCharsets.UTF_8);
        assertTrue(content.contains("\"seq\":0"), content);
        assertTrue(content.endsWith("\n"), content);
    }

    @Test
    void adapterBasedChunkFollowsResponseContentType() throws IOException {
        HttpWriterFactory adapterFactory = new HttpWriterFactory(null, adapter());
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setContentType("application/json");

        ChunkStreamWriter writer = adapterFactory.chunk(response);
        writer.writeChunk(Map.of("seq", 2));

        String content = response.getContentAsString(StandardCharsets.UTF_8);
        assertTrue(content.contains("\"seq\":2"), "Content-Type=application/json → 对象序列化为 JSON: " + content);
    }

    @Test
    void adapterBasedChunkTextPlainStringWritesRawBytes() throws IOException {
        HttpWriterFactory adapterFactory = new HttpWriterFactory(null, adapter());
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setContentType("text/plain;charset=UTF-8");

        ChunkStreamWriter writer = adapterFactory.chunk(response);
        writer.writeChunk((Object) "裸文本块");

        assertEquals("裸文本块", response.getContentAsString(StandardCharsets.UTF_8));
    }

    /**
     * 手工构造带转换器的 MVC 适配器（真实容器由 WebMvc 自动配置注入）。
     */
    private static RequestMappingHandlerAdapter adapter() {
        RequestMappingHandlerAdapter adapter = new RequestMappingHandlerAdapter();
        adapter.setMessageConverters(List.of(
                new StringHttpMessageConverter(StandardCharsets.UTF_8),
                new JacksonJsonHttpMessageConverter()));
        return adapter;
    }

}
