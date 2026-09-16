package io.github.oatelauser.springplus.web.stream;

import io.github.oatelauser.springplus.web.response.SimpleResponse;
import io.github.oatelauser.springplus.web.utils.JsonUtils;
import org.jspecify.annotations.Nullable;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * NDJSON（Newline Delimited JSON）流式写入器：逐行 JSON 响应。
 * <p>
 * 每条记录序列化为一行 JSON 并以 {@code \n} 终止，随后 flush——客户端按行解析即可。
 * 典型场景：LLM 逐 token 输出、日志流、批量数据导出。
 *
 * <h3>每行必含 {@code \n} 是协议红线</h3>
 * <p>
 * 若经 Spring MVC 消息转换器裸发对象（如 {@code ResponseBodyEmitter.send(obj)}），
 * 序列化结果<b>不会</b>追加换行符，产出的「JSON 粘连流」不是合法 NDJSON——
 * 本写入器在每条记录后统一补 {@code \n}，保证逐行可解析。
 *
 * <h3>写入语义</h3>
 * <p>
 * 所有写入方法遵循「锁外序列化，锁内写入」原则（同 {@link SseStreamWriter}）。
 * JSON 序列化会把字符串内部的换行转义为 {@code \n} 字面量，因此每条记录物理上必然单行。
 *
 * <h3>错误处理</h3>
 * <p>
 * 接口按 {@code produces = "application/x-ndjson"} 声明后，流中途抛出的异常会由全局异常处理
 * 自动补写一行 {@link SimpleResponse} 错误记录（{@code NdjsonExceptionProcessor}），业务方无须
 * try/catch；{@link #writeErrorLine(String, String)} 仅供业务方主动写错误行时使用。
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * @GetMapping(value = "/tokens", produces = MediaType.APPLICATION_NDJSON_VALUE)
 * public void tokens(HttpServletResponse response) throws IOException {
 *     try (NdjsonStreamWriter writer = streamWriterFactory.ndjson(response)) {
 *         for (Token t : llm.generate()) {
 *             writer.writeLine(t);
 *         }
 *     } // 异常直接抛出，全局处理器补写错误行
 * }
 * }</pre>
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2026-08-21
 * @since 1.0
 */
public class NdjsonStreamWriter extends AbstractStreamWriter {

    /** NDJSON 记录终止符。 */
    private static final byte[] LF = "\n".getBytes(StandardCharsets.UTF_8);


    public NdjsonStreamWriter(OutputStream outputStream, JsonMapper jsonMapper) {
        super(outputStream, jsonMapper);
    }

    /**
     * 工厂路径构造器：序列化按转换器矩阵匹配
     * （仍固定按 {@link MediaType#APPLICATION_JSON} 匹配，见 {@link #writeLine(Object)}）。
     *
     * @param outputStream      响应输出流
     * @param jsonMapper        JSON 序列化器（{@code String} 值的字面量包装用；null 兜底全局共享实例）
     * @param messageConverters MVC 消息转换器列表（如 {@code adapter.getMessageConverters()}）
     */
    public NdjsonStreamWriter(OutputStream outputStream, @Nullable JsonMapper jsonMapper,
            List<HttpMessageConverter<?>> messageConverters) {
        super(outputStream, jsonMapper, messageConverters);
    }

    /**
     * 写入一行 JSON 记录：对象序列化为 JSON + {@code \n} + flush。
     * <p>
     * 序列化固定按 {@link MediaType#APPLICATION_JSON} 匹配（NDJSON 记录语义就是 JSON，
     * 不拿流类型 {@code application/x-ndjson} 去试——默认 Jackson 转换器不认它，
     * 且自定义转换器接管会破坏「每行一个 JSON」协议红线）。converter 版序列化器下，
     * 业务注册的 Jackson 定制（序列化器模块等）自动生效。
     * <p>
     * <b>String 值特殊处理</b>：converter 矩阵里 {@code StringHttpMessageConverter} 以
     * {@code *}&#47;{@code *} 通配先于 Jackson 命中，会把字符串<b>裸写</b>（MVC 对
     * {@code @ResponseBody String} 的同款行为）——一行裸文本不是合法 JSON 记录，
     * 违反本写入器的协议红线，因此 {@code String} 强制包装为 JSON 字符串字面量
     * （{@code "text"}）。裸文本输出请用 {@link #writeRawLine(String)}。
     *
     * @param data 记录内容（任意可 JSON 序列化对象）
     * @return this（链式）
     * @throws IOException IO异常
     */
    public NdjsonStreamWriter writeLine(Object data) throws IOException {
        byte[] payload = data instanceof String s
                ? this.quoteAsStringLiteral(s)
                : this.serializePayload(data, MediaType.APPLICATION_JSON);
        this.writeRecord(payload);
        return this;
    }

    /**
     * 把字符串包装为 JSON 字符串字面量字节（含引号与转义），保护 NDJSON 逐行协议。
     */
    private byte[] quoteAsStringLiteral(String value) {
        JsonMapper mapper = this.jsonMapper != null ? this.jsonMapper : JsonUtils.shared();
        return mapper.writeValueAsString(value).getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 直写一行原始文本并补 {@code \n}：调用方自行保证内容是单行合法 JSON（或约定格式）。
     *
     * @param line 单行内容（不含换行符）
     * @return this（链式）
     * @throws IOException IO异常
     */
    public NdjsonStreamWriter writeRawLine(String line) throws IOException {
        this.writeRecord(line.getBytes(StandardCharsets.UTF_8));
        return this;
    }

    /**
     * 便捷方法：写入一行 {@link SimpleResponse} 错误记录。
     * <p>
     * 通常无须手动调用——{@code produces = "application/x-ndjson"} 的接口抛出异常后，
     * 全局异常处理会自动补写错误行；仅在业务方需要在正常流程中主动输出错误记录时使用。
     *
     * @param code    业务错误码（如 A0106）
     * @param message 用户可见错误文案
     * @return this（链式）
     * @throws IOException IO异常
     */
    public NdjsonStreamWriter writeErrorLine(String code, String message) throws IOException {
        return this.writeLine(SimpleResponse.fail(code, message, null));
    }

    /**
     * 锁内写记录 + 换行 + flush。
     */
    private void writeRecord(byte[] bytes) throws IOException {
        lock.lock();
        try {
            this.checkClosed();
            outputStream.write(bytes);
            outputStream.write(LF);
            outputStream.flush();
        } finally {
            lock.unlock();
        }
    }

}
