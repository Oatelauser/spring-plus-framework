package io.github.oatelauser.springplus.web.stream;

import io.github.oatelauser.springplus.web.utils.JsonUtils;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link NdjsonStreamWriter} 单元测试：核心断言「每条记录必以 \n 终止」（NDJSON 协议红线），
 * 含 v2.1 转换器矩阵路径下 String 值的协议保护。
 *
 * @author Oatelauser
 * @date 2026-08-21
 * @since 1.0
 */
class NdjsonStreamWriterTest {

    private NdjsonStreamWriter newWriter(ByteArrayOutputStream out) {
        return new NdjsonStreamWriter(out, JsonUtils.shared());
    }

    private static List<HttpMessageConverter<?>> converters() {
        return List.of(
                new StringHttpMessageConverter(StandardCharsets.UTF_8),
                new JacksonJsonHttpMessageConverter());
    }

    @Test
    void writeLineTerminatesWithNewline() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        newWriter(out).writeLine(Map.of("seq", 0, "content", "first"));

        String content = out.toString(StandardCharsets.UTF_8);
        assertTrue(content.endsWith("\n"), "NDJSON 每条记录必须以 \\n 终止: " + content);
        assertEquals(1, content.chars().filter(c -> c == '\n').count(), "单条记录物理上应只有行尾一个换行");
    }

    @Test
    void multipleLinesAreLineParseable() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        NdjsonStreamWriter writer = newWriter(out);

        writer.writeLine(Map.of("seq", 0));
        writer.writeLine(Map.of("seq", 1));
        writer.writeLine(Map.of("seq", 2));

        String content = out.toString(StandardCharsets.UTF_8);
        List<String> lines = content.lines().toList();
        assertEquals(3, lines.size(), "3 条记录应可解析为 3 行: " + content);
        assertTrue(lines.get(0).contains("\"seq\":0"));
        assertTrue(lines.get(2).contains("\"seq\":2"));
    }

    @Test
    void innerNewlineInStringStaysSinglePhysicalLine() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        // 字符串内部含换行：JSON 序列化必须转义为 \n 字面量，物理行不拆
        newWriter(out).writeLine(Map.of("text", "line1\nline2"));

        String content = out.toString(StandardCharsets.UTF_8);
        assertEquals(1, content.lines().count(), "含内部换行的记录仍应是单物理行: " + content);
    }

    @Test
    void writeRawLineWritesTextPlusNewline() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        newWriter(out).writeRawLine("{\"raw\":true}");

        assertEquals("{\"raw\":true}\n", out.toString(StandardCharsets.UTF_8));
    }

    @Test
    void converterPathWriteLineQuotesStringToProtectProtocol() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        NdjsonStreamWriter writer = new NdjsonStreamWriter(out, null, converters());

        writer.writeLine("delta");

        assertEquals("\"delta\"\n", out.toString(StandardCharsets.UTF_8),
                "writeLine(String) 必须产出 JSON 字符串字面量：矩阵里 StringHttpMessageConverter "
                        + "以 */* 通配先于 Jackson 命中会裸写，而一行裸文本不是合法 NDJSON 记录");
    }

    @Test
    void converterPathWriteLineMapGoesThroughJackson() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        NdjsonStreamWriter writer = new NdjsonStreamWriter(out, null, converters());

        writer.writeLine(Map.of("seq", 0, "content", "first"));

        String content = out.toString(StandardCharsets.UTF_8);
        assertTrue(content.contains("\"seq\":0"), content);
        assertTrue(content.endsWith("\n"), "转换器路径同样遵守行终止协议");
    }

    @Test
    void writeErrorLineWritesServerResponseShape() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        newWriter(out).writeErrorLine("A0501", "请求次数超出限制");

        String content = out.toString(StandardCharsets.UTF_8);
        assertTrue(content.contains("\"code\":\"A0501\""), content);
        assertTrue(content.contains("\"message\":\"请求次数超出限制\""), content);
        assertTrue(content.contains("\"success\":false"), content);
        assertTrue(content.endsWith("\n"), "错误行同样要以 \\n 终止");
    }

}
