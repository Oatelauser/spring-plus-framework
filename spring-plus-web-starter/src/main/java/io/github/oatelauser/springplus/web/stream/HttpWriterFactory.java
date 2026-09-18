package io.github.oatelauser.springplus.web.stream;

import io.github.oatelauser.springplus.web.utils.JsonUtils;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.util.CollectionUtils;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 流式写入器统一工厂：一行代码拿到「协议头已配好」的写入器。
 * <p>
 * 职责边界：写入器（{@link SseStreamWriter} / {@link NdjsonStreamWriter} /
 * {@link ChunkStreamWriter} / {@link FileDownloadWriter}）只管往 {@code OutputStream}
 * 写数据；<b>协议响应头</b>（Content-Type / Cache-Control / 关缓冲）集中在本工厂设置，
 * 避免每个业务接口复制粘贴头配置。
 *
 * <h3>各工厂方法的头部策略</h3>
 * <table border="1">
 *   <tr><th>方法</th><th>Content-Type</th><th>其他头</th></tr>
 *   <tr><td>{@link #sse}</td><td>text/event-stream;charset=UTF-8</td><td>Cache-Control:no-cache、X-Accel-Buffering:no</td></tr>
 *   <tr><td>{@link #ndjson}</td><td>application/x-ndjson;charset=UTF-8</td><td>X-Accel-Buffering:no</td></tr>
 *   <tr><td>{@link #chunk}</td><td><b>不设置</b>（载体泛化，由调用方按文本/二进制指定）</td><td>—</td></tr>
 *   <tr><td>{@link #download}</td><td>按扩展名推断（兜底 octet-stream）</td><td>Content-Disposition:attachment/inline（RFC 5987 编码中文文件名）</td></tr>
 * </table>
 * <p>
 * 流式三兄弟（sse/ndjson/chunk）统一 {@code setBufferSize(0)}：关闭容器响应缓冲，
 * 保证每次 flush 即时送达客户端。
 *
 * <h3>X-Accel-Buffering: no 的作用</h3>
 * <p>
 * Nginx 反代会缓冲上游响应再整段转发，破坏流式语义（客户端收不到逐块推送）；
 * 该头告知 Nginx 对本响应关闭代理缓冲。
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2026-08-21
 * @since 1.0
 */
public class HttpWriterFactory {

    /**
     * Nginx 代理缓冲开关头：no = 关闭反代缓冲，保证流式（含错误行）即时送达。
     * <p>流式写入器与 NDJSON 错误行渲染器共用（{@code NdjsonExceptionProcessor}）。
     */
    @SuppressWarnings("all")
    public static final String X_ACCEL_BUFFERING = "X-Accel-Buffering";

    private final JsonMapper jsonMapper;

    /**
     * MVC 消息转换器矩阵（v2.1 序列化开放点）：流式写入器的 Object 载荷
     * 与 Controller 返回值走同一套转换规则。
     */
    private final List<HttpMessageConverter<?>> messageConverters;

    /**
     * 兼容构造器：Object 序列化一律 JSON（裸构造 / 测试场景）。
     *
     * @param jsonMapper JSON 序列化器；null 时兜底用 {@link JsonUtils#shared()} 全局共享实例
     */
    public HttpWriterFactory(@Nullable JsonMapper jsonMapper) {
        this(jsonMapper, (List<HttpMessageConverter<?>>) null);
    }

    /**
     * 完整构造器（Bean 装配路径）：注入 MVC 适配器后，各写入器的 Object 载荷
     * （{@code writeChunk(Object)} / {@code writeLine(Object)} / {@code writeData(Object)}）
     * 直接按适配器的消息转换器矩阵匹配——格式由「类型 × MediaType」决定，
     * 业务自定义消息转换器自动生效（复刻 {@link HttpResponseWriter} 机制）。
     *
     * @param jsonMapper                   JSON 序列化器；null 兜底全局共享实例
     * @param requestMappingHandlerAdapter MVC 适配器；null 时矩阵退化为 Jackson-only
     */
    public HttpWriterFactory(@Nullable JsonMapper jsonMapper, @Nullable RequestMappingHandlerAdapter requestMappingHandlerAdapter) {
        this(jsonMapper, requestMappingHandlerAdapter == null ? null : requestMappingHandlerAdapter.getMessageConverters());
    }

    /**
     * 构造底层实例化对象
     */
    public HttpWriterFactory(@Nullable JsonMapper jsonMapper, @Nullable List<HttpMessageConverter<?>> messageConverters) {
        this.jsonMapper = jsonMapper != null ? jsonMapper : JsonUtils.shared();
        this.messageConverters = CollectionUtils.isEmpty(messageConverters) ?
                List.of(AbstractStreamWriter.defaultJsonConverter(this.jsonMapper)) : messageConverters;
    }

    /**
     * 创建 SSE 写入器：text/event-stream + no-cache + 关缓冲。
     *
     * @param response 当前响应
     * @return SSE 写入器
     * @throws IOException IO异常
     */
    public SseStreamWriter sse(HttpServletResponse response) throws IOException {
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.TEXT_EVENT_STREAM_VALUE);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache");
        response.setHeader(X_ACCEL_BUFFERING, "no");
        response.setBufferSize(0);
        return new SseStreamWriter(response.getOutputStream(), this.jsonMapper, this.messageConverters);
    }

    /**
     * 创建 NDJSON 写入器：application/x-ndjson + 关缓冲。
     *
     * @param response 当前响应
     * @return NDJSON 写入器
     * @throws IOException IO异常
     */
    public NdjsonStreamWriter ndjson(HttpServletResponse response) throws IOException {
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_NDJSON_VALUE);
        response.setHeader(X_ACCEL_BUFFERING, "no");
        response.setBufferSize(0);
        return new NdjsonStreamWriter(response.getOutputStream(), this.jsonMapper, this.messageConverters);
    }

    /**
     * 创建按块写入器：不设置 Content-Type——块载体泛化（文本/二进制皆可），
     * 由调用方显式指定（如 {@code response.setContentType("text/plain;charset=UTF-8")}）。
     *
     * @param response 当前响应
     * @return 按块写入器
     * @throws IOException IO异常
     */
    public ChunkStreamWriter chunk(HttpServletResponse response) throws IOException {
        response.setBufferSize(0);
        // 载荷类型延迟到每次 writeChunk(Object) 时读取：创建写入器后再设 Content-Type 也有效
        return new ChunkStreamWriter(response.getOutputStream(), this.messageConverters,
                response::getContentType);
    }

    /**
     * 创建文件下载写入器（attachment 语义：浏览器弹下载框）。
     *
     * @param response 当前响应
     * @param filename 下载文件名（支持中文，RFC 5987 编码）
     * @return 下载写入器
     * @throws IOException IO异常
     */
    public FileDownloadWriter download(HttpServletResponse response, String filename) throws IOException {
        return this.download(response, filename, false);
    }

    /**
     * 创建文件下载写入器。
     *
     * @param response 当前响应
     * @param filename 下载文件名（支持中文，RFC 5987 编码）
     * @param inline   true = inline（浏览器内直接打开，如 PDF 预览）；false = attachment（下载）
     * @return 下载写入器
     * @throws IOException IO异常
     */
    public FileDownloadWriter download(HttpServletResponse response, String filename, boolean inline) throws IOException {
        MediaType mediaType = MediaTypeFactory.getMediaType(filename)
                .orElse(MediaType.APPLICATION_OCTET_STREAM);
        response.setContentType(mediaType.toString());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        // ContentDisposition 工具类构造（RFC 6266/5987）：非 ASCII 文件名时同时产出
        // filename="ISO-8859-1 兜底名"（不可映射字符替换为 _）与 filename*=UTF-8''百分号编码，
        // 现代浏览器取后者——不再手拼字符串，编码规则由框架维护。
        ContentDisposition disposition = (inline ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(filename, StandardCharsets.UTF_8).build();
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, disposition.toString());
        return new FileDownloadWriter(response.getOutputStream());
    }

}
