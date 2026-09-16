package io.github.oatelauser.springplus.web.error.annotation;

import io.github.oatelauser.springplus.web.error.descriptor.LogStackPolicy;
import io.github.oatelauser.springplus.web.error.descriptor.OutputProtocol;
import io.github.oatelauser.springplus.web.error.output.ExceptionBodyCustomizer;
import org.springframework.core.annotation.AliasFor;
import org.springframework.http.HttpStatus;

import java.lang.annotation.*;

import static io.github.oatelauser.springplus.web.response.ServerStatus.SERVER_INTERNAL_CODE;

/**
 * SSE 协议专用 {@link ExceptionResponse}（v3.0：仅存 {@code event} / {@code retry} 两个专属字段）。
 * <p>
 * 通过元注解 {@code @ExceptionResponse(protocols = HTTP_SSE)} 锁死协议，公共字段经
 * {@code @AliasFor} 桥接回父注解。效果：仅当异常发生接口的输出协议是 SSE 时才生效。
 * <p>
 * v3.0 变化：原 {@code handshakeStatus} 属性删除——它与 JSON 的 {@code httpStatus} 同义
 * （都是「真实 HTTP 状态码」），统一上移为父注解 {@link ExceptionResponse#httpStatus()}，
 * 经本注解同名属性桥接。写法兼容：原
 * {@code @SseExceptionResponse(handshakeStatus = HttpStatus.SERVICE_UNAVAILABLE)} 改写为
 * {@code @SseExceptionResponse(httpStatus = HttpStatus.SERVICE_UNAVAILABLE)}。
 * <p>
 * 语义提醒：EventSource 客户端只在 HTTP 200 握手下收事件，非 200 会让客户端直接 onerror
 * 而收不到错误事件——{@code httpStatus} 对 SSE 只应作用于「流未开始」的握手失败场景。
 *
 * 示例代码：
 *
 * @formatter:off
 * {@snippet :
 * @RestController
 * @RequestMapping("/api/subscribe")
 * public class SubscribeController {
 *
 *     @SseExceptionResponse(value = RateLimitException.class, code = "B0300",
 *                           msg = "订阅限流", event = "rate-limit", retry = 5_000)
 *     @GetMapping(value = "/user", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
 *     public SseEmitter subscribe(@RequestParam Long userId) { ... }
 * }
 * }
 * @formatter:on
 *
 * @author Oatelauser
 * @date 2026-08-14
 * @since 2.0
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(SseExceptionResponse.List.class)
@Target({ ElementType.METHOD, ElementType.TYPE })
@ExceptionResponse(protocols = OutputProtocol.HTTP_SSE)
public @interface SseExceptionResponse {

    /**
     * 处理的兜底异常
     */
    @AliasFor(annotation = ExceptionResponse.class)
    Class<? extends Throwable>[] value() default Exception.class;

    /**
     * 响应code
     */
    @AliasFor(annotation = ExceptionResponse.class)
    String code() default SERVER_INTERNAL_CODE;

    /**
     * 响应异常信息
     */
    @AliasFor(annotation = ExceptionResponse.class)
    String msg() default "";

    /**
     * 是否把异常信息作为msg
     */
    @AliasFor(annotation = ExceptionResponse.class)
    boolean showException() default false;

    /**
     * 优先级（数值越小优先级越高），桥接父注解 {@link ExceptionResponse#order()}
     */
    @AliasFor(annotation = ExceptionResponse.class)
    int order() default 0;

    /**
     * 握手期 HTTP 状态码（v3.0 桥接父注解 {@link ExceptionResponse#httpStatus()}）。
     * <p>
     * <b>替换 v2 的 {@code handshakeStatus}</b>（同名义合并）。仅「流未开始」时生效；
     * EventSource 只在 200 下收事件，非 200 客户端直接 onerror——慎用。
     */
    @AliasFor(annotation = ExceptionResponse.class)
    HttpStatus httpStatus() default HttpStatus.OK;

    /**
     * SSE 专属：错误事件的 {@code event} 名称（缺省取全局配置
     * {@code GlobalExceptionProperties.Sse.defaultEventName}，v2 缺省 "app-error"）。
     */
    String event() default "";

    /**
     * SSE 专属：错误事件携带的 {@code retry} 重连间隔（毫秒）。{@code -1} 表示不输出该字段，
     * 客户端保持自己的默认重连策略。
     */
    long retry() default -1L;

    /**
     * 自定义错误响应体，桥接父注解 {@link ExceptionResponse#output()}。
     * <p>
     * 必须是 Spring Bean（启动 fail-fast）；产物会被 JSON 序列化后作为错误事件的 data 载荷。
     */
    @AliasFor(annotation = ExceptionResponse.class)
    Class<? extends ExceptionBodyCustomizer> output() default ExceptionBodyCustomizer.class;

    /**
     * 日志堆栈策略，桥接父注解 {@link ExceptionResponse#logPolicy()}
     */
    @AliasFor(annotation = ExceptionResponse.class)
    LogStackPolicy logPolicy() default LogStackPolicy.DEFAULT;

    /**
     * {@link Repeatable} 容器，编译器自动包装，业务方无需手写。
     */
    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ ElementType.METHOD, ElementType.TYPE })
    @interface List {
        /**
         * 承载多条 {@link SseExceptionResponse}。
         */
        SseExceptionResponse[] value() default {};
    }

}
