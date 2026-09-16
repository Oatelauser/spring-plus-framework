package io.github.oatelauser.springplus.web.stream;

import io.github.oatelauser.springplus.web.error.ServiceException;
import io.github.oatelauser.springplus.web.utils.JsonUtils;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayOutputStream;
import java.io.Flushable;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import static io.github.oatelauser.springplus.web.response.ClientStatus.CONTENT_TYPE_NOT_SUPPORTED;

/**
 * 流式写入器抽象基类，提供序列化、刷新和关闭等通用能力。
 * <p>所有写入操作均为线程安全（synchronized）。</p>
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2026-03-08
 * @since 1.0
 */
public class AbstractStreamWriter implements AutoCloseable, Flushable {

    protected final JsonMapper jsonMapper;
    protected final OutputStream outputStream;

    /**
     * MVC 消息转换器矩阵（v2.1 序列化开放点，机制同 {@link HttpResponseWriter}）：
     * Object 载荷按「值类型 × MediaType」匹配，第一个 {@code canWrite} 命中者负责序列化——
     * 业务注册的自定义转换器（XML / 自定义文本…）对流式载荷自动生效。
     */
    protected final List<HttpMessageConverter<?>> messageConverters;

    protected volatile boolean closed = false;
    protected final ReentrantLock lock = new ReentrantLock();

    /**
     * 兼容构造器：转换器矩阵只含 Jackson，Object 载荷一律 JSON（裸构造 / 测试场景）。
     */
    protected AbstractStreamWriter(OutputStream outputStream, @Nullable JsonMapper jsonMapper) {
        this(outputStream, jsonMapper, List.of(defaultJsonConverter(jsonMapper)));
    }

    /**
     * 完整构造器（工厂路径）：注入 MVC 全套转换器（如 {@code adapter.getMessageConverters()}）。
     *
     * @param outputStream      响应输出流
     * @param jsonMapper        JSON 序列化器（{@code String} 字面量包装等特殊场景用）；可为 null
     * @param messageConverters 消息转换器列表（按顺序匹配，先注册先命中）；null 视为空矩阵
     */
    protected AbstractStreamWriter(OutputStream outputStream, @Nullable JsonMapper jsonMapper,
            @Nullable List<HttpMessageConverter<?>> messageConverters) {
        this.jsonMapper = jsonMapper;
        this.outputStream = outputStream;
        this.messageConverters = messageConverters != null ? List.copyOf(messageConverters) : List.of();
    }

    /**
     * 按转换器矩阵序列化业务对象为载荷字节（v2.1 序列化开放点，机制同
     * {@link HttpResponseWriter#writeTo}）：遍历 {@link #messageConverters}，
     * 第一个 {@code canWrite(值类型, mediaType)} 命中者负责序列化。
     * <ul>
     *   <li>{@code mediaType == null} → 按 MVC 语义视为通配（首个支持该值类型的转换器命中）</li>
     *   <li>无人认领 → {@link ServiceException}（CONTENT_TYPE_NOT_SUPPORTED），
     *       不静默回退 JSON——「业务以为在吐文本实际吐了 JSON」比显式报错更坑</li>
     * </ul>
     * <p>
     * converter 写入<b>内存缓冲</b>拿 {@code byte[]}：流式写入器遵循
     * 「锁外序列化 → 锁内 write+flush」锁模型，序列化产物须先成字节再进锁。
     * <b>必须在锁外调用。</b>
     *
     * @param data      业务对象
     * @param mediaType 目标载荷类型；null 视为通配
     * @return 序列化后的载荷字节
     * @throws IOException 序列化底层 IO 失败
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    protected byte[] serializePayload(Object data, @Nullable MediaType mediaType) throws IOException {
        for (HttpMessageConverter converter : this.messageConverters) {
            if (converter.canWrite(data.getClass(), mediaType)) {
                BufferingOutputMessage buffer = new BufferingOutputMessage();
                converter.write(data, mediaType, buffer);
                return buffer.toByteArray();
            }
        }
        throw new ServiceException(CONTENT_TYPE_NOT_SUPPORTED);
    }

    protected void checkClosed() throws IOException {
        if (closed) {
            throw new IOException("StreamWriter is already closed");
        }
    }

    /**
     * 写入器是否已关闭
     */
    public boolean isClosed() {
        return closed;
    }

    @Override
    public void flush() throws IOException {
        lock.lock();
        try {
            if (!closed && outputStream != null) {
                outputStream.flush();
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * 关闭写入器，刷新所有缓冲数据。
     * <p>底层 OutputStream 的生命周期由 Servlet 容器管理，此处仅做 flush。</p>
     */
    @Override
    public synchronized void close() throws IOException {
        if (!closed && outputStream != null) {
            closed = true;
            outputStream.flush();
        }
    }

    /**
     * Jackson-only 兜底转换器：无 MVC 环境时矩阵仍可用（Object → JSON）。
     */
    static JacksonJsonHttpMessageConverter defaultJsonConverter(@Nullable JsonMapper jsonMapper) {
        return new JacksonJsonHttpMessageConverter(jsonMapper != null ? jsonMapper : JsonUtils.shared());
    }

    /**
     * 内存缓冲输出消息：converter 写到这里，完成后整体取字节。
     * <p>
     * converter 可能回写 Content-Type / Content-Length 到 headers（如
     * {@code AbstractHttpMessageConverter#addDefaultHeaders}），缓冲实现照单全收、用后即弃。
     */
    @NullMarked
    private static final class BufferingOutputMessage implements HttpOutputMessage {
        private final HttpHeaders headers = new HttpHeaders();
        private final ByteArrayOutputStream body = new ByteArrayOutputStream();

        @Override
        public OutputStream getBody() {
            return this.body;
        }

        @Override
        public HttpHeaders getHeaders() {
            return this.headers;
        }

        byte[] toByteArray() {
            return this.body.toByteArray();
        }
    }

}
