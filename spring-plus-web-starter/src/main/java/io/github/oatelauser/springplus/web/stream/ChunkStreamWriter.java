package io.github.oatelauser.springplus.web.stream;

import org.jspecify.annotations.Nullable;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * 按块（Chunk）流式写入器：把响应内容拆成多个数据块逐块写出，客户端边收边渲染。
 * <p>
 * 适用于大文本 / 二进制流式输出、进度推送等「载体泛化」的场景（文本、二进制皆可）。
 *
 * <h3>与 chunked 传输编码的关系</h3>
 * <p>
 * HTTP/1.1 的 {@code Transfer-Encoding: chunked} 分帧由 <b>Servlet 容器自动完成</b>：
 * 响应未设 {@code Content-Length} 且调用 {@code flush()} 时，Tomcat 自动按块编码发出。
 * 本写入器<b>不</b>手写 {@code size\r\n} 分帧——容器会再包一层，导致流损坏。
 *
 * <h3>写入语义</h3>
 * <p>
 * 所有写入方法遵循「锁外序列化，锁内写入」原则（同 {@link SseStreamWriter}）：
 * <ol>
 *   <li>在锁外完成对象序列化（避免持锁期间执行潜在耗时的 JSON 序列化）</li>
 *   <li>获取 {@code ReentrantLock}</li>
 *   <li>检查关闭状态</li>
 *   <li>写入数据并 flush（flush 即向客户端发出一个数据块）</li>
 *   <li>释放锁</li>
 * </ol>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // Content-Type 由调用方按载体指定（文本 / 二进制），工厂 chunk(response) 不代设
 * response.setContentType("text/plain;charset=UTF-8");
 * try (ChunkStreamWriter writer = streamWriterFactory.chunk(response)) {
 *     for (String line : bigData) {
 *         writer.writeChunk(line + "\n");
 *     }
 * }
 * }</pre>
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2026-08-21
 * @since 1.0
 */
public class ChunkStreamWriter extends AbstractStreamWriter {

    /**
     * 载荷类型的延迟来源（通常绑定 {@code response::getContentType}）。
     * <p>
     * 每次 {@link #writeChunk(Object)} 时读取——调用方在创建写入器<b>之后</b>、首块写出<b>之前</b>
     * 设置 Content-Type 均有效；裸构造为 {@code null}（序列化走 JSON 兜底，不关心类型）。
     */
    @Nullable
    private final Supplier<String> payloadContentType;

    public ChunkStreamWriter(OutputStream outputStream, JsonMapper jsonMapper) {
        super(outputStream, jsonMapper);
        this.payloadContentType = null;
    }

    /**
     * 工厂路径构造器：转换器矩阵 + 载荷类型来源。
     *
     * @param outputStream       响应输出流
     * @param messageConverters  MVC 消息转换器列表（决定 Object → 什么格式）
     * @param payloadContentType 载荷类型来源（如 {@code response::getContentType}）
     */
    public ChunkStreamWriter(OutputStream outputStream, List<HttpMessageConverter<?>> messageConverters,
            @Nullable Supplier<String> payloadContentType) {
        super(outputStream, null, messageConverters);
        this.payloadContentType = payloadContentType;
    }

    /**
     * 写入一个对象块：按<b>响应 Content-Type</b> 匹配消息转换器序列化（复刻
     * {@link HttpResponseWriter#writeTo(Object, MediaType, jakarta.servlet.http.HttpServletResponse)}
     * 语义）——响应是什么类型，对象就序列化成什么格式；随后 flush。
     * <ul>
     *   <li>Content-Type 为 {@code text/plain} + {@code String} 值 → 字符串字节</li>
     *   <li>Content-Type 为 {@code application/json} + 任意对象 → JSON</li>
     *   <li>业务注册的自定义转换器（XML / protobuf…）→ 自定义格式</li>
     * </ul>
     * <p>
     * 裸构造（JsonMapper 兜底）时忽略类型，一律 JSON。
     *
     * @param data 数据块内容
     * @return this（链式）
     * @throws IOException IO异常
     */
    public ChunkStreamWriter writeChunk(Object data) throws IOException {
        this.writeAndFlush(this.serializePayload(data, this.resolvePayloadType()));
        return this;
    }

    /**
     * 写入一个对象块，显式指定载荷类型（动态场景 / 构造后才确定类型时使用）。
     *
     * @param data      数据块内容
     * @param mediaType 载荷类型（非空）
     * @return this（链式）
     * @throws IOException IO异常
     */
    public ChunkStreamWriter writeChunk(Object data, MediaType mediaType) throws IOException {
        this.writeAndFlush(this.serializePayload(data,
                Objects.requireNonNull(mediaType, "mediaType 不能为空")));
        return this;
    }

    /**
     * 写入一个文本块（UTF-8 编码），随后 flush。
     *
     * @param text 文本块内容
     * @return this（链式）
     * @throws IOException IO异常
     */
    public ChunkStreamWriter writeChunk(String text) throws IOException {
        this.writeAndFlush(text.getBytes(StandardCharsets.UTF_8));
        return this;
    }

    /**
     * 写入一个二进制块，随后 flush。
     *
     * @param data 二进制块内容
     * @return this（链式）
     * @throws IOException IO异常
     */
    public ChunkStreamWriter writeChunk(byte[] data) throws IOException {
        this.writeAndFlush(data);
        return this;
    }

    /**
     * 解析当前载荷类型：读 {@link #payloadContentType}，未设置则报错引导
     * （先 {@code setContentType} 再写，或改用显式带 {@link MediaType} 的重载）。
     */
    @Nullable
    private MediaType resolvePayloadType() {
        if (this.payloadContentType == null) {
            return null;
        }
        String contentType = this.payloadContentType.get();
        if (contentType == null || contentType.isBlank()) {
            throw new IllegalStateException(
                    "chunk(Object) 无法确定载荷类型：请先 response.setContentType(...)，"
                            + "或改用 writeChunk(Object, MediaType) 显式指定");
        }
        return MediaType.parseMediaType(contentType);
    }

    /**
     * 锁内写入 + flush：flush 时容器把缓冲数据按 chunked 编码发出。
     */
    private void writeAndFlush(byte[] bytes) throws IOException {
        lock.lock();
        try {
            this.checkClosed();
            outputStream.write(bytes);
            outputStream.flush();
        } finally {
            lock.unlock();
        }
    }

}
