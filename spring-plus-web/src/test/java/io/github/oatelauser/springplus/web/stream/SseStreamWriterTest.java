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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SseStreamWriter} 单元测试：SSE 帧格式、多行拆行、
 * String 裸文本历史行为、v2.1 序列化开放点（converter 矩阵 + 显式 MediaType 重载）。
 *
 * @author Oatelauser
 * @date 2026-08-22
 * @since 2.1
 */
class SseStreamWriterTest {

    private static final MediaType TEXT_PLAIN_UTF8 = MediaType.parseMediaType("text/plain;charset=UTF-8");

    private static List<HttpMessageConverter<?>> converters() {
        return List.of(
                new StringHttpMessageConverter(StandardCharsets.UTF_8),
                new JacksonJsonHttpMessageConverter());
    }

    @Test
    void bareWriteDataMapEmitsJsonFrame() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        new SseStreamWriter(out, JsonUtils.shared()).writeData(Map.of("seq", 1));

        assertEquals("data: {\"seq\":1}\n\n", out.toString(StandardCharsets.UTF_8),
                "非 String 对象默认按 application/json 序列化，帧尾为空行");
    }

    @Test
    void bareWriteDataStringWritesRawText() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        new SseStreamWriter(out, JsonUtils.shared()).writeData("hello");

        assertEquals("data: hello\n\n", out.toString(StandardCharsets.UTF_8),
                "String 直写裸文本（合法 SSE 载荷，历史行为保持），不得再包一层 JSON 引号");
    }

    @Test
    void multilineStringSplitsIntoDataFields() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        new SseStreamWriter(out, JsonUtils.shared()).writeData("第一行\n第二行");

        assertEquals("data: 第一行\ndata: 第二行\n\n", out.toString(StandardCharsets.UTF_8),
                "SSE 规范要求多行数据的每一行独立携带 data: 前缀");
    }

    @Test
    void bareWriteEventEmitsEventAndDataFields() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        new SseStreamWriter(out, JsonUtils.shared()).writeEvent("tick", Map.of("seq", 1));

        assertEquals("event: tick\ndata: {\"seq\":1}\n\n", out.toString(StandardCharsets.UTF_8));
    }

    @Test
    void bareWriteEventWithIdEmitsIdEventData() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        new SseStreamWriter(out, JsonUtils.shared()).writeEvent("7", "tick", Map.of("seq", 1));

        assertEquals("id: 7\nevent: tick\ndata: {\"seq\":1}\n\n", out.toString(StandardCharsets.UTF_8));
    }

    @Test
    void converterPathWriteDataMapGoesThroughJacksonConverter() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        SseStreamWriter writer = new SseStreamWriter(out, null, converters());

        writer.writeData(Map.of("seq", 1, "content", "delta"));

        String frame = out.toString(StandardCharsets.UTF_8);
        assertTrue(frame.contains("\"seq\":1"), frame);
        assertTrue(frame.contains("\"content\":\"delta\""), frame);
        assertTrue(frame.endsWith("\n\n"), "帧尾必须是空行: " + frame);
    }

    @Test
    void converterPathWriteDataStringStaysRaw() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        SseStreamWriter writer = new SseStreamWriter(out, null, converters());

        writer.writeData("hello");

        assertEquals("data: hello\n\n", out.toString(StandardCharsets.UTF_8),
                "String 在进入序列化器之前直通，converter 版行为与裸构造一致");
    }

    @Test
    void converterPathWriteDataStringWithTextPlainStaysRaw() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        SseStreamWriter writer = new SseStreamWriter(out, null, converters());

        writer.writeData("hello", TEXT_PLAIN_UTF8);

        assertEquals("data: hello\n\n", out.toString(StandardCharsets.UTF_8),
                "String 不经过 converter 匹配，text/plain 也不会触发无匹配异常");
    }

    @Test
    void converterPathMapAsTextPlainThrowsServiceException() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        SseStreamWriter writer = new SseStreamWriter(out, null, converters());

        assertThrows(ServiceException.class,
                () -> writer.writeData(Map.of("seq", 1), TEXT_PLAIN_UTF8),
                "Map × text/plain 在转换器矩阵中无人认领 → CONTENT_TYPE_NOT_SUPPORTED");
    }

    @Test
    void converterPathWriteEventExplicitMediaTypeUsesJackson() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        SseStreamWriter writer = new SseStreamWriter(out, null, converters());

        writer.writeEvent("tick", Map.of("seq", 2), MediaType.APPLICATION_JSON);

        String frame = out.toString(StandardCharsets.UTF_8);
        assertTrue(frame.startsWith("event: tick\n"), frame);
        assertTrue(frame.contains("\"seq\":2"), frame);
    }

    @Test
    void converterPathWriteEventIdExplicitMediaTypeEmitsFullFrame() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        SseStreamWriter writer = new SseStreamWriter(out, null, converters());

        writer.writeEvent("9", "tick", "hello", TEXT_PLAIN_UTF8);

        assertEquals("id: 9\nevent: tick\ndata: hello\n\n", out.toString(StandardCharsets.UTF_8),
                "带 ID + 显式 MediaType 的四参重载完整成帧");
    }

    @Test
    void writeAfterCloseThrows() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        SseStreamWriter writer = new SseStreamWriter(out, JsonUtils.shared());
        writer.writeData("ok");
        writer.close();

        assertThrows(IOException.class, () -> writer.writeData("late"));
        assertTrue(writer.isClosed());
    }

}
