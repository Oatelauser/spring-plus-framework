package io.github.oatelauser.springplus.web.error.descriptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.Nullable;
import org.springframework.web.method.HandlerMethod;

import java.util.Map;

/**
 * 异常渲染上下文，在渲染阶段传递给 {@code ExceptionBodyRenderer} / {@code ExceptionRenderer}。
 * <p>
 * 它把「描述错误」的 {@link ErrorDescriptor} 与「请求运行时信息」（request / response /
 * handlerMethod / protocol）打包到一起，让自定义渲染器能在一个方法参数里拿到全部所需信息，
 * 而无需到处传参或依赖容器。
 *
 * <h3>为什么是 record</h3>
 * <p>
 * 渲染上下文是不可变的快照：引擎在解析完 {@code ErrorDescriptor}、探测完协议后一次性构造，
 * 渲染期间只读。record 既保证不可变，又自动生成访问器，契合「只读快照」语义。
 *
 * <h3>为什么需要 response</h3>
 * <p>
 * SSE 渲染必须直接写 servlet response（绕开 Spring MVC 返回值处理器，否则
 * {@code ResponseEntity<SseEmitter>} 会被 {@code HttpEntityMethodProcessor} 误用消息转换器
 * 写出，引发 "No converter for SseEmitter" 错误）。SSE B/C 档（流内异步线程）拿不到 response，
 * 此字段允许为 null。
 *
 * <h3>便捷访问器</h3>
 * <p>
 * {@link #exception()} / {@link #code()} / {@link #message()} / {@link #details()} 都是转发到
 * 内嵌的 {@link ErrorDescriptor}，让自定义 {@code bodyCustomizer} 写起来更简洁
 * （{@code ctx.message()} 比 {@code ctx.descriptor().message()} 顺眼）。
 *
 * @author Oatelauser
 * @date 2026-06-15
 * @since 2.0
 */
public record ExceptionContext(
        /* 完整的错误描述对象（含 code/message/details/httpStatus/sseEvent 等全部元数据）。 */
        ErrorDescriptor descriptor,
        /* 当前请求；SSE B/C 档（流内异常）可能为 null（脱离 HTTP 请求线程）。 */
        @Nullable HttpServletRequest request,
        /* 当前响应；SSE A 档渲染器需要直接写帧到 servlet 输出流，B/C 档为 null。 */
        @Nullable HttpServletResponse response,
        /* 命中的 Controller 方法；Filter / HandlerMapping 阶段异常时可能为 null。 */
        @Nullable HandlerMethod handlerMethod,
        /* 当前请求探测出的响应协议（HTTP_JSON / HTTP_SSE / ...）。 */
        OutputProtocol protocol
) {

    /**
     * 转发：触发本次错误的异常对象。
     */
    public Throwable exception() {
        return descriptor.getError();
    }

    /**
     * 转发：错误码。
     */
    public String code() {
        return descriptor.getCode();
    }

    /**
     * 转发：用户可见错误文案。
     */
    public String message() {
        return descriptor.getMessage();
    }

    /**
     * 转发：结构化补充信息。
     */
    public Map<String, Object> details() {
        return descriptor.getDetails();
    }
}
