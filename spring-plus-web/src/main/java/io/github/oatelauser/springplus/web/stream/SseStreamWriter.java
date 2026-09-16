package io.github.oatelauser.springplus.web.stream;

import org.jspecify.annotations.Nullable;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;

/**
 * SSE (Server-Sent Events) 流式写入器。
 * <p>
 * 所有写入方法遵循「锁外序列化，锁内写入」原则：
 * <ol>
 *   <li>在锁外完成对象序列化（避免持锁期间执行潜在耗时的 JSON 序列化）</li>
 *   <li>获取 {@link ReentrantLock}</li>
 *   <li>检查关闭状态</li>
 *   <li>写入数据并 flush</li>
 *   <li>释放锁</li>
 * </ol>
 * </p>
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2026-03-08
 * @since 1.0
 */
public class SseStreamWriter extends AbstractStreamWriter {

    private static final byte[] DATA_PREFIX = "data: ".getBytes(StandardCharsets.UTF_8);
    private static final byte[] EVENT_PREFIX = "event: ".getBytes(StandardCharsets.UTF_8);
    private static final byte[] ID_PREFIX = "id: ".getBytes(StandardCharsets.UTF_8);
    private static final byte[] RETRY_PREFIX = "retry: ".getBytes(StandardCharsets.UTF_8);
    private static final byte[] COMMENT_PREFIX = ": ".getBytes(StandardCharsets.UTF_8);
    private static final byte[] LF = "\n".getBytes(StandardCharsets.UTF_8);

    public SseStreamWriter(OutputStream outputStream, JsonMapper jsonMapper) {
        super(outputStream, jsonMapper);
    }

    /**
     * 工厂路径构造器：Object 序列化按转换器矩阵匹配（v2.1 序列化开放点，
     * 机制同 {@link HttpResponseWriter}）。
     *
     * @param outputStream      响应输出流
     * @param jsonMapper        JSON 序列化器；可为 null
     * @param messageConverters MVC 消息转换器列表（如 {@code adapter.getMessageConverters()}）
     */
    public SseStreamWriter(OutputStream outputStream, @Nullable JsonMapper jsonMapper,
            List<HttpMessageConverter<?>> messageConverters) {
        super(outputStream, jsonMapper, messageConverters);
    }

    /**
     * 写入 data 事件：对象默认按 {@code application/json} 序列化。
     * <p>输出：{@code data: {json}\n\n}</p>
     */
    public SseStreamWriter writeData(Object data) throws IOException {
        return this.writeDataField(this.payloadAsString(data));
    }

    /**
     * 写入 data 事件，显式指定载荷类型（XML / 自定义文本等格式的开口）。
     *
     * @param data      事件数据
     * @param mediaType 载荷类型（非空），converter 版据此匹配消息转换器
     */
    public SseStreamWriter writeData(Object data, MediaType mediaType) throws IOException {
        return this.writeDataField(this.payloadAsString(data,
                Objects.requireNonNull(mediaType, "mediaType 不能为空")));
    }

    private SseStreamWriter writeDataField(String value) throws IOException {
        lock.lock();
        try {
            this.checkClosed();
            this.writeSseField(DATA_PREFIX, value);
            outputStream.write(LF);
            outputStream.flush();
        } finally {
            lock.unlock();
        }
        return this;
    }

    /**
     * 写入带事件类型的 SSE 事件。
     * <pre>
     * event: {event}
     * data: {json}
     *
     * </pre>
     */
    public SseStreamWriter writeEvent(String event, Object data) throws IOException {
        return this.writeEventInternal(event, this.payloadAsString(data));
    }

    /**
     * 写入带事件类型的 SSE 事件，显式指定载荷类型。
     *
     * @param event     事件类型
     * @param data      事件数据
     * @param mediaType 载荷类型（非空），converter 版据此匹配消息转换器
     */
    public SseStreamWriter writeEvent(String event, Object data, MediaType mediaType) throws IOException {
        return this.writeEventInternal(event, this.payloadAsString(data,
                Objects.requireNonNull(mediaType, "mediaType 不能为空")));
    }

    private SseStreamWriter writeEventInternal(String event, String value) throws IOException {
        byte[] bytes = event.getBytes(StandardCharsets.UTF_8);
        lock.lock();
        try {
            this.checkClosed();
            this.writeField(EVENT_PREFIX, bytes);
            this.writeSseField(DATA_PREFIX, value);
            outputStream.write(LF);
            outputStream.flush();
        } finally {
            lock.unlock();
        }
        return this;
    }

    /**
     * 写入带 ID 和事件类型的 SSE 事件。
     * <pre>
     * id: {id}
     * event: {event}
     * data: {json}
     *
     * </pre>
     */
    public SseStreamWriter writeEvent(String id, String event, Object data) throws IOException {
        return this.writeEvent(id, event, data, MediaType.APPLICATION_JSON);
    }

    /**
     * 写入带 ID 和事件类型的 SSE 事件，显式指定载荷类型。
     *
     * @param id        事件 ID
     * @param event     事件类型
     * @param data      事件数据
     * @param mediaType 载荷类型（非空），converter 版据此匹配消息转换器
     */
    public SseStreamWriter writeEvent(String id, String event, Object data, MediaType mediaType) throws IOException {
        String text = this.payloadAsString(data, Objects.requireNonNull(mediaType, "mediaType 不能为空"));
        byte[] idBytes = id.getBytes(StandardCharsets.UTF_8);
        byte[] eventBytes = event.getBytes(StandardCharsets.UTF_8);
        lock.lock();
        try {
            checkClosed();
            writeField(ID_PREFIX, idBytes);
            writeField(EVENT_PREFIX, eventBytes);
            writeSseField(DATA_PREFIX, text);
            outputStream.write(LF);
            outputStream.flush();
        } finally {
            lock.unlock();
        }
        return this;
    }

    /**
     * 把业务对象转为 SSE data 字段文本（v2.1 序列化开放点）。
     * <ul>
     *   <li>{@link String} → 裸文本直写（合法 SSE 载荷，历史行为保持）</li>
     *   <li>其他对象 → 按 {@code application/json} 走消息转换器矩阵
     *       （业务自定义转换器生效；显式重载可指定 XML / 自定义文本等任意类型）</li>
     * </ul>
     */
    private String payloadAsString(Object data) throws IOException {
        return this.payloadAsString(data, MediaType.APPLICATION_JSON);
    }

    private String payloadAsString(Object data, MediaType mediaType) throws IOException {
        if (data instanceof String s) {
            return s;
        }
        return new String(this.serializePayload(data, mediaType), StandardCharsets.UTF_8);
    }

    /**
     * 写入 SSE 字段，自动处理多行数据拆行。
     * <p>SSE 规范要求多行数据的每一行都需要独立的字段前缀。</p>
     */
    private void writeSseField(byte[] prefix, String value) throws IOException {
        String[] lines = StringUtils.delimitedListToStringArray(value, "\n");
        if (ObjectUtils.isEmpty(lines)) {
            this.writeField(prefix, value.getBytes(StandardCharsets.UTF_8));
            return;
        }
        for (String line : lines) {
            this.writeField(prefix, line.getBytes(StandardCharsets.UTF_8));
        }
    }

    private void writeField(byte[] prefix, byte[] value) throws IOException {
        outputStream.write(prefix);
        outputStream.write(value);
        outputStream.write(LF);
    }

}
