package io.github.oatelauser.springplus.web.stream;

import io.github.oatelauser.springplus.web.error.ServiceException;
import io.github.oatelauser.springplus.web.utils.JsonUtils;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ChunkStreamWriter} 单元测试：块写入内容、链式返回、关闭后拒绝写入，
 * 含 v2.1 转换器矩阵路径（自动解析响应 Content-Type / 显式 MediaType / 无匹配报错）。
 *
 * @author Oatelauser
 * @date 2026-08-21
 * @since 1.0
 */
class ChunkStreamWriterTest {

    private static final MediaType TEXT_PLAIN_UTF8 = MediaType.parseMediaType("text/plain;charset=UTF-8");

    private ChunkStreamWriter newWriter(ByteArrayOutputStream out) {
        return new ChunkStreamWriter(out, JsonUtils.shared());
    }

    private static List<HttpMessageConverter<?>> converters() {
        return List.of(
                new StringHttpMessageConverter(StandardCharsets.UTF_8),
                new JacksonJsonHttpMessageConverter());
    }

    @Test
    void writeChunkStringWritesUtf8Bytes() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ChunkStreamWriter writer = newWriter(out);

        ChunkStreamWriter returned = writer.writeChunk("第一块").writeChunk("second");

        assertEquals("第一块second", out.toString(StandardCharsets.UTF_8));
        assertEquals(writer, returned, "writeChunk 应链式返回 this");
    }

    @Test
    void writeChunkBytesWritesRaw() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] payload = { 0x00, 0x01, 0x02, (byte) 0xFF };

        newWriter(out).writeChunk(payload);

        assertArrayEquals(payload, out.toByteArray());
    }

    @Test
    void writeChunkObjectSerializesJson() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        newWriter(out).writeChunk(java.util.Map.of("seq", 1, "content", "chunk"));

        String json = out.toString(StandardCharsets.UTF_8);
        assertTrue(json.contains("\"seq\":1"), "对象应序列化为 JSON: " + json);
        assertTrue(json.contains("\"content\":\"chunk\""), json);
    }

    @Test
    void converterPathAutoResolvesContentTypeFromSupplier() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ChunkStreamWriter writer = new ChunkStreamWriter(out, converters(), () -> "application/json");

        writer.writeChunk(java.util.Map.of("seq", 1, "content", "chunk"));

        String content = out.toString(StandardCharsets.UTF_8);
        assertTrue(content.contains("\"seq\":1"), content);
        assertTrue(content.contains("\"content\":\"chunk\""), content);
    }

    @Test
    void converterPathTextPlainStringWritesRawBytes() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ChunkStreamWriter writer = new ChunkStreamWriter(out, converters(), () -> "text/plain;charset=UTF-8");

        writer.writeChunk((Object) "中文块");

        assertEquals("中文块", out.toString(StandardCharsets.UTF_8),
                "String × text/plain → StringHttpMessageConverter 直写字节（MVC 同款语义）");
    }

    @Test
    void converterPathWithoutContentTypeFailsFast() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ChunkStreamWriter writer = new ChunkStreamWriter(out, converters(), () -> null);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> writer.writeChunk(java.util.Map.of("seq", 1)));
        assertTrue(ex.getMessage().contains("setContentType"), "报错须引导先设 Content-Type: " + ex.getMessage());
    }

    @Test
    void converterPathExplicitMediaTypeOverload() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ChunkStreamWriter writer = new ChunkStreamWriter(out, converters(), () -> null);

        writer.writeChunk("中文块", TEXT_PLAIN_UTF8);

        assertEquals("中文块", out.toString(StandardCharsets.UTF_8));
    }

    @Test
    void converterPathUnmatchedCombinationThrowsServiceException() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ChunkStreamWriter writer = new ChunkStreamWriter(out, converters(), () -> "text/plain;charset=UTF-8");

        // Map × text/plain：StringHttpMessageConverter 只写 String，Jackson 只认 JSON 系 → 无人认领
        assertThrows(ServiceException.class,
                () -> writer.writeChunk(java.util.Map.of("seq", 1)));
    }

    @Test
    void writeAfterCloseThrows() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ChunkStreamWriter writer = newWriter(out);
        writer.writeChunk("ok");
        writer.close();

        assertThrows(IOException.class, () -> writer.writeChunk("late"));
        assertTrue(writer.isClosed());
    }

}
