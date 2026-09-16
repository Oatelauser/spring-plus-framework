package io.github.oatelauser.springplus.web.error.output;

import io.github.oatelauser.springplus.web.error.descriptor.ErrorDescriptor;
import io.github.oatelauser.springplus.web.error.descriptor.ExceptionContext;
import io.github.oatelauser.springplus.web.error.descriptor.OutputProtocol;
import io.github.oatelauser.springplus.web.response.SimpleResponse;
import io.github.oatelauser.springplus.web.stream.HttpWriterFactory;
import io.github.oatelauser.springplus.web.utils.JsonUtils;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * NDJSON 协议渲染器（{@link SseExceptionProcessor} 的逐行流式镜像，v3.0）。
 * <p>
 * 把 {@link ErrorDescriptor} 翻译成<b>一行</b>错误 JSON 记录。NDJSON 接口
 * （{@code produces = "application/x-ndjson"}）的异常按响应提交状态分两态渲染：
 *
 * <h3>两态渲染</h3>
 * <ul>
 *   <li><b>流未开始</b>（{@code !response.isCommitted()}）：设 HTTP 状态码
 *       （{@link ErrorDescriptor#getStatusIntent()}，缺省 200 兼容 v1.0 业务码风格）+
 *       {@code Content-Type: application/x-ndjson}，写一行错误 JSON 后收流。</li>
 *   <li><b>流已开始</b>（已写出数据行，响应已提交）：头与状态码均已发出不可改——
 *       在流上<b>补写一行错误记录</b>再 flush。客户端逐行解析时能凭 {@code code != "00000"}
 *       感知失败，而不是把「半截流」当正常结束。这是本渲染器实现
 *       {@link ExceptionOutputProcessor#handlesCommitted() handlesCommitted() = true} 的原因：
 *       引擎（{@code ExceptionOutputEngine}）在已提交时仍会把错误交给它。</li>
 * </ul>
 *
 * <h3>v3.0：状态码不再借道 JsonErrorHint</h3>
 * <p>
 * v2.x 的 NDJSON 复用 JSON 协议的 {@code JsonErrorHint.httpStatus}——错误行就是一行 JSON，
 * 状态码语义确实同源，但「借别家协议的 hint」正是 hint 家族膨胀的根源。v3.0 状态码升维为
 * 协议无关的 {@code statusIntent}，NDJSON 侧只在自己能兑现的时机（流未开始）读取它。
 *
 * <h3>错误行格式</h3>
 * <p>
 * 与 JSON 协议错误体<b>同构</b>：一行 {@code SimpleResponse} 的 JSON 序列化
 * （{@code {"code":..,"message":..,"data":null,"details":..,"success":false}}），前端一套解析
 * 逻辑通吃 JSON 与 NDJSON 两种协议。自定义 {@code bodyCustomizer}
 * （{@link ExceptionBodyCustomizer#transform}）优先，语义与 JSON/SSE 渲染器一致。
 *
 * <h3>写出通道</h3>
 * <p>
 * 直写 {@code response.getOutputStream()} 并返回 {@code null}（绕开 Spring MVC 返回值处理器，
 * 同 SSE A 档策略）——数据写入器（{@code NdjsonStreamWriter}）也走输出流，避免
 * {@code getWriter()}/{@code getOutputStream()} 混用抛 {@code IllegalStateException}。
 *
 * @author Oatelauser
 * @date 2026-08-21
 * @since 2.1
 */
public class NdjsonExceptionProcessor implements ExceptionOutputProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(NdjsonExceptionProcessor.class);

    /**
     * 错误行序列化器：与 {@code StreamWriterFactory} 同源注入（容器 JsonMapper 优先），
     * 保证<b>数据行与错误行序列化风格一致</b>（业务注册的 Jackson 定制对两者同时生效，
     * v2.1 边界加固——此前直写 {@code JsonUtils.shared()} 会造成两种序列化风格分叉）。
     */
    private final JsonMapper jsonMapper;

    public NdjsonExceptionProcessor(@Nullable JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper != null ? jsonMapper : JsonUtils.shared();
    }

    @Override
    public OutputProtocol protocol() {
        return OutputProtocol.NDJSON;
    }

    /**
     * 支持已提交响应补写：NDJSON 是逐行协议，可以在已发出的流后追加一行错误记录。
     */
    @Override
    public boolean handlesCommitted() {
        return true;
    }

    @Override
    public Object handle(ErrorDescriptor descriptor, ExceptionContext ctx) {
        HttpServletResponse response = ctx.response();
        if (response == null) {
            // 防御性回退：NDJSON 同步写入模型下渲染必有 response，到这里说明调用方用错了 API。
            LOG.warn("NDJSON 渲染缺少 HttpServletResponse，无法写出错误行（请检查调用方）");
            return null;
        }
        try {
            if (response.isCommitted()) {
                this.appendErrorLine(response, descriptor, ctx);
            } else {
                this.writeFreshError(response, descriptor, ctx);
            }
        } catch (IOException ex) {
            // 客户端已断开等：流多半也写不动了，只记录不再尝试转写。
            LOG.warn("NDJSON 错误行写入失败 | code={}", descriptor.getCode(), ex);
        }
        // 返回 null：告诉 Spring「我自己处理了响应」，跳过返回值处理器。
        return null;
    }

    /**
     * 流未开始：设状态码 + 协议头，写一行错误记录后收流。
     * <p>
     * 状态码语义与 JSON 协议同源（{@code statusIntent}，协议无关意图），null → 缺省 200。
     */
    private void writeFreshError(HttpServletResponse response, ErrorDescriptor descriptor,
            ExceptionContext ctx) throws IOException {
        HttpStatus status = descriptor.getStatusIntent() != null ? descriptor.getStatusIntent() : HttpStatus.OK;
        response.setStatus(status.value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_NDJSON_VALUE);
        response.setHeader(HttpWriterFactory.X_ACCEL_BUFFERING, "no");
        this.writeErrorRecord(response, descriptor, ctx);
    }

    /**
     * 流已开始：头与状态码均已发出（提交后再 set 也无效），直接补写一行错误记录并 flush。
     */
    private void appendErrorLine(HttpServletResponse response, ErrorDescriptor descriptor,
            ExceptionContext ctx) throws IOException {
        this.writeErrorRecord(response, descriptor, ctx);
    }

    /**
     * 解析错误行 body：自定义定制器（{@code bodyCustomizer.transform}）优先，回退默认 {@link SimpleResponse}。
     */
    private Object resolveBody(ErrorDescriptor descriptor, ExceptionContext ctx) {
        if (descriptor.getBodyCustomizer() != null) {
            Object body = descriptor.getBodyCustomizer().transform(ctx);
            if (body != null) {
                return body;
            }
        }
        return SimpleResponse.fail(descriptor.getCode(), descriptor.getMessage(), descriptor.getDetails());
    }

    /**
     * 写一行错误记录（JSON + {@code \n}）到响应输出流并 flush。
     * <p>
     * JSON 序列化会转义字符串内部换行，错误行物理上必然单行，符合 NDJSON 逐行约束。
     * 序列化用与数据行同源的 {@link #jsonMapper}（见字段注释）。
     * <p>
     * <b>序列化兜底</b>（v2.2）：自定义 {@code bodyCustomizer} 的产物若序列化失败（自引用对象、
     * getter 抛错等——炸点在异常处理器内部，是最恶劣的排查现场），降级写标准
     * {@link SimpleResponse} 错误行并 ERROR 落盘，保证「错误行永远写得出」。
     * 捕获 {@code RuntimeException}：Jackson 3 的 {@code JacksonException} 及 getter 抛出的
     * 运行时异常都是它的子类；深度递归由 Jackson 嵌套上限拦截（抛 {@code JacksonException}），
     * 不会以 {@code StackOverflowError} 形态逃逸。
     */
    private void writeErrorRecord(HttpServletResponse response, ErrorDescriptor descriptor,
            ExceptionContext ctx) throws IOException {
        Object body = this.resolveBody(descriptor, ctx);
        String json;
        try {
            json = this.jsonMapper.writeValueAsString(body);
        } catch (RuntimeException e) {
            LOG.error("NDJSON 错误行序列化失败，降级标准 SimpleResponse 行 | code={}", descriptor.getCode(), e);
            json = this.jsonMapper.writeValueAsString(
                    SimpleResponse.fail(descriptor.getCode(), descriptor.getMessage(), descriptor.getDetails()));
        }
        response.getOutputStream().write((json + "\n").getBytes(StandardCharsets.UTF_8));
        response.getOutputStream().flush();
    }

}
