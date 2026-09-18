package io.github.oatelauser.springplus.web.error.output;

import io.github.oatelauser.springplus.web.autoconfigure.GlobalExceptionProperties;
import io.github.oatelauser.springplus.web.error.descriptor.ErrorDescriptor;
import io.github.oatelauser.springplus.web.error.descriptor.ExceptionContext;
import io.github.oatelauser.springplus.web.error.descriptor.OutputProtocol;
import io.github.oatelauser.springplus.web.response.SimpleResponse;
import io.github.oatelauser.springplus.web.utils.JsonUtils;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * SSE 协议渲染器（v3.0，设计文档 8.1，外加 v2.0 上线后修订的 servlet 直写策略）。
 * <p>
 * 把 {@link ErrorDescriptor} 翻译成一个 SSE 错误事件。
 *
 * <h3>为什么 A 档不再返回 {@code ResponseEntity<SseEmitter>}</h3>
 * <p>
 * v2.0 早期实现让 {@link #handle} 返回 {@code ResponseEntity<SseEmitter>}，但
 * Spring MVC 的返回值处理器选择是按<b>方法声明返回类型</b>决策的：{@code @ExceptionHandler}
 * 方法的声明返回类型是 {@code Object}，不是 {@code SseEmitter}/{@code ResponseBodyEmitter}，
 * 所以走的是 {@code HttpEntityMethodProcessor}（普通 Body 写出器），
 * 而非 {@code ResponseBodyEmitterReturnValueHandler}。后者会用消息转换器把 emitter 当成 body
 * 序列化，命中 {@code text/event-stream} 时找不到 converter，抛
 * {@code HttpMessageNotWritableException: No converter for SseEmitter}。
 * <p>
 * 修复策略：A 档直接拿 {@link HttpServletResponse} 写出 SSE 帧 + flush + 不写
 * {@code Content-Length} → 让连接保持流式 → 写完一个事件后返回 {@code null}（告诉 Spring 我自己处理了）。
 * 完全绕开 Spring 的返回值处理器和消息转换器栈。
 *
 * <h3>三档共用渲染逻辑</h3>
 * <p>
 * {@link #sendErrorEvent} 是 B/C 档的写帧入口（向业务自有 {@link SseEmitter} 写帧），不变。
 * A 档独立走 {@link #writeServletSse}（直接写 response 输出流），与 B/C 档形态不同但语义一致：
 * 同一份 body 解析逻辑、同一个事件名、同一份 retry 处理。
 *
 * <h3>v3.0：handshakeStatus 删除，握手状态读 {@code statusIntent}</h3>
 * <p>
 * v2.x 在 {@code SseErrorHint} 上单设 {@code handshakeStatus}，与 JSON 的
 * {@code JsonErrorHint.httpStatus} 是同一语义的两份表达。v3.0 统一为协议无关的
 * {@link ErrorDescriptor#getStatusIntent()}：A 档（握手期，流未开始）状态码尚未发出，
 * 此时读取生效；B/C 档流内异常状态码早已发出，天然不读。{@link SseErrorHint} 收窄为
 * 真正 SSE 专属的渲染参数（event / retry）。
 *
 * @author Oatelauser
 * @date 2026-06-15
 * @since 2.0
 */
public class SseExceptionProcessor implements ExceptionOutputProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(SseExceptionProcessor.class);

    /**
     * SSE 帧分隔（按规范每个事件块以空行结束）。
     */
    private static final String FRAME_TERMINATOR = "\n\n";

    /**
     * retry 未指定的哨兵值（仅 {@code > 0} 时写 retry 字段）。
     */
    private static final long RETRY_UNSPECIFIED = -1L;

    private final GlobalExceptionProperties properties;

    public SseExceptionProcessor(GlobalExceptionProperties properties) {
        this.properties = properties;
    }

    @Override
    public OutputProtocol protocol() {
        return OutputProtocol.HTTP_SSE;
    }

    /**
     * A 档握手期渲染：直接写 servlet response 输出流后返回 null（绕开 Spring 返回值处理器）。
     * <p>
     * 当 ctx 中没有 response（罕见，理论上 A 档一定有）时退化为返回 {@link SseEmitter}——但这条
     * 路径在 Spring MVC 下不可靠，仅作防御性回退。
     */
    @Override
    public Object handle(ErrorDescriptor descriptor, ExceptionContext ctx) {
        HttpServletResponse response = ctx.response();
        if (response == null) {
            // 防御性回退：理论上 A 档必有 response，到这里说明调用方用错了 API（应走 B/C 档 sendErrorEvent）。
            LOG.warn("SSE A 档渲染缺少 HttpServletResponse，无法直写 servlet 输出流（请检查调用方）");
            return null;
        }
        try {
            writeServletSse(response, descriptor, ctx);
        } catch (IOException ex) {
            // 客户端已断开等：response 多半也写不动了，只记录不再尝试转写。
            LOG.warn("SSE 错误事件写入 servlet 输出流失败 | code={}", descriptor.getCode(), ex);
        }
        // 返回 null：告诉 Spring「我自己处理了响应」，跳过返回值处理器。
        return null;
    }

    /**
     * 直接把一个 SSE 错误事件写到 servlet response 输出流（A 档专用，绕开 Spring MVC 写出栈）。
     * <p>
     * 设置 {@code Content-Type: text/event-stream}、关 cache、关 Nginx 缓冲，然后按 SSE 规范
     * 写出一帧 {@code event: ... \n data: ... \n\n} 并 flush 关流。这里<b>不</b>设置
     * {@code Content-Length}——否则部分浏览器会等读满才把 onmessage 派发出去。
     */
    private void writeServletSse(HttpServletResponse response, ErrorDescriptor descriptor,
            ExceptionContext ctx) throws IOException {
        // 从 sealed ErrorHint 下钻取出 SSE 专属 hint（event / retry；默认 hint 兜底）。
        SseErrorHint hint = resolveHint(descriptor);

        // 协议头：SSE 标准 + 关缓冲（X-Accel-Buffering 防 Nginx 反代缓冲）。
        // A 档 = 握手期（流未开始），状态码尚未发出 → statusIntent 在此生效；null → 缺省 200。
        HttpStatus status = descriptor.getStatusIntent() != null ? descriptor.getStatusIntent() : HttpStatus.OK;
        response.setStatus(status.value());
        response.setContentType(MediaType.TEXT_EVENT_STREAM_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache");

        Object body = resolveBody(descriptor, ctx);
        // body 是任意对象（默认 SimpleResponse；自定义 bodyCustomizer 可返回 Map/DTO）；
        // 用全局 JsonMapper 序列化成一行 JSON 写入 SSE 的 data 字段。
        String dataJson = JsonUtils.shared().writeValueAsString(body);

        StringBuilder frame = new StringBuilder(dataJson.length() + 64);
        // SSE 响应拆分防护（CWE-113）：event 名来自注解/配置/bodyCustomizer，注入换行可伪造事件帧
        String eventName = hint.event();
        if (eventName.indexOf('\n') >= 0 || eventName.indexOf('\r') >= 0) {
            eventName = "app-error";
        }
        if (hint.retry() > 0) {
            // retry 字段（毫秒）：客户端断线重连间隔。
            frame.append("retry: ").append(hint.retry()).append('\n');
        }
        frame.append("event: ").append(eventName).append('\n');
        // SSE 协议要求 data 不能内含原始换行——序列化后的 JSON 字符串本身不会有，但万一
        // bodyCustomizer 返回了带 \n 的对象，按 SSE 规范应拆成多行 data:。这里做防御性 escape。
        frame.append("data: ").append(dataJson.replace("\n", "\ndata: ")).append(FRAME_TERMINATOR);

        response.getWriter().write(frame.toString());
        response.getWriter().flush();
    }

    /**
     * 向一个业务自有 {@link SseEmitter} 写入错误事件（B/C 档专用，A 档不再走这条）。
     * <p>
     * body 优先级：自定义 {@code bodyCustomizer} 返回非 null → 用之；否则默认 {@link SimpleResponse#fail}。
     * 事件名：hint 的 event（null 时已由 {@link #resolveHint} 用全局默认兜底）。
     * retry：仅当 {@code > 0} 时写入 {@code reconnectTime}。
     * <p>
     * B/C 档处于流内（状态码早已发出），不读 {@code statusIntent}——它只在 A 档握手期有效。
     *
     * @param emitter    目标 emitter（业务异步线程内已经在用的那个）
     * @param descriptor 错误描述
     * @param ctx        渲染上下文
     */
    public void sendErrorEvent(SseEmitter emitter, ErrorDescriptor descriptor, ExceptionContext ctx) throws IOException {
        // A 档与 B/C 档共用同一套 hint 解析（resolveHint），保证三档输出一致。
        SseErrorHint hint = resolveHint(descriptor);
        Object body = resolveBody(descriptor, ctx);
        SseEmitter.SseEventBuilder event = SseEmitter.event().name(hint.event())
                .data(body, MediaType.APPLICATION_JSON);
        if (hint.retry() > 0) {
            event.reconnectTime(hint.retry());
        }
        emitter.send(event);
    }

    /**
     * 用 Java 21 模式匹配从 {@code descriptor.hint} 下钻取出 {@link SseErrorHint}。
     * <p>
     * 命中时，对 {@code event} 字段做<b>配置兜底</b>——派生注解声明的 event 是 {@code null} / 空串
     * 时，用全局配置 {@code web.error-response.sse.default-event-name}（默认 {@code app-error}）兜底。
     * 这样 hint 工厂（{@code AnnotationToTemplateConverter}）保持纯函数、不依赖配置，
     * 配置兜底只在本渲染器出现，职责清晰。未命中（普通 {@code @ExceptionResponse} 通吃所有协议）
     * → 全局默认值构造默认 hint。
     */
    private SseErrorHint resolveHint(ErrorDescriptor descriptor) {
        String defaultEvent = this.properties.getSse().getDefaultEventName();
        if (descriptor.getHint() instanceof SseErrorHint h) {
            String event = StringUtils.hasText(h.event()) ? h.event() : defaultEvent;
            return new SseErrorHint(event, h.retry());
        }
        return new SseErrorHint(defaultEvent, RETRY_UNSPECIFIED);
    }

    /**
     * 解析事件 body：自定义定制器优先，回退默认 SimpleResponse。
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

}
