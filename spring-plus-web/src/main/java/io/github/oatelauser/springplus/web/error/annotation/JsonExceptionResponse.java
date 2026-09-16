package io.github.oatelauser.springplus.web.error.annotation;

import io.github.oatelauser.springplus.web.error.descriptor.LogStackPolicy;
import io.github.oatelauser.springplus.web.error.descriptor.OutputProtocol;
import io.github.oatelauser.springplus.web.error.output.ExceptionBodyCustomizer;
import org.springframework.core.annotation.AliasFor;
import org.springframework.http.HttpStatus;

import java.lang.annotation.*;

import static io.github.oatelauser.springplus.web.response.ServerStatus.SERVER_INTERNAL_CODE;

/**
 * JSON 协议专用 {@link ExceptionResponse}（v3.0：零协议专属字段）。
 * <p>
 * 通过元注解 {@code @ExceptionResponse(protocols = HTTP_JSON)} 锁死协议，公共字段经
 * {@code @AliasFor} 桥接回父注解（对齐 Spring {@code @GetMapping} ← {@code @RequestMapping}
 * 组合模型）。效果：仅当异常发生接口的输出协议是 JSON 时才生效（协议过滤器 O(1) 排除）。
 * <p>
 * v3.0 变化：原 {@code httpStatus} 属性上移到父注解（协议无关），本注解自身不再持有任何
 * 协议专属属性——JSON 错误体的全部信息（code/msg/状态码/自定义响应体）都是协议无关的。
 * 写法完全兼容：{@code @JsonExceptionResponse(httpStatus = HttpStatus.NOT_FOUND, ...)} 依旧合法，
 * 只是属性经桥接落在父注解上。
 *
 * 示例代码：
 *
 * @formatter:off
 * {@snippet :
 * @RestController
 * @RequestMapping("/api/user")
 * public class UserController {
 *
 *     // 仅 JSON 协议生效；SSE / NDJSON 接口抛 UserNotFoundException 时不消费此规则
 *     @JsonExceptionResponse(value = UserNotFoundException.class, code = "B0100",
 *                            msg = "用户不存在", httpStatus = HttpStatus.NOT_FOUND)
 *     @GetMapping("/{id}")
 *     public SimpleResponse<UserView> getUser(@PathVariable Long id) { ... }
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
@Repeatable(JsonExceptionResponse.List.class)
@Target({ ElementType.METHOD, ElementType.TYPE })
@ExceptionResponse(protocols = OutputProtocol.HTTP_JSON)
public @interface JsonExceptionResponse {

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
     * 真实 HTTP 状态码（v3.0 桥接父注解 {@link ExceptionResponse#httpStatus()}，缺省 200）。
     * <p>
     * 仅 JSON 协议消费：写入 {@code ResponseEntity} 的状态码。
     */
    @AliasFor(annotation = ExceptionResponse.class)
    HttpStatus httpStatus() default HttpStatus.OK;

    /**
     * 自定义错误响应体，桥接父注解 {@link ExceptionResponse#output()}。
     * <p>
     * 必须是 Spring Bean（启动 fail-fast），典型如微信回调的 {@code errcode/errmsg} 结构。
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
         * 承载多条 {@link JsonExceptionResponse}。
         */
        JsonExceptionResponse[] value() default {};
    }

}
