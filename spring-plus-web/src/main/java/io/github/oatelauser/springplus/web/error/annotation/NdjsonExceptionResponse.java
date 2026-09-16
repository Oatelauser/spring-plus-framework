package io.github.oatelauser.springplus.web.error.annotation;

import io.github.oatelauser.springplus.web.error.descriptor.LogStackPolicy;
import io.github.oatelauser.springplus.web.error.descriptor.OutputProtocol;
import io.github.oatelauser.springplus.web.error.output.ExceptionBodyCustomizer;
import org.springframework.core.annotation.AliasFor;
import org.springframework.http.HttpStatus;

import java.lang.annotation.*;

import static io.github.oatelauser.springplus.web.response.ServerStatus.SERVER_INTERNAL_CODE;

/**
 * NDJSON 协议专用 {@link ExceptionResponse}（v3.0：零协议专属字段）。
 * <p>
 * 通过元注解 {@code @ExceptionResponse(protocols = NDJSON)} 锁死协议，公共字段经
 * {@code @AliasFor} 桥接回父注解。效果：仅当异常发生接口的输出协议是 NDJSON
 * （{@code produces = "application/x-ndjson"} 逐行流式接口）时才生效。
 * <p>
 * NDJSON 的错误行与 JSON 错误体同构（一行 {@code SimpleResponse} JSON），不存在协议专属
 * 渲染参数——v3.0 起与 {@code @JsonExceptionResponse} 一样零专属字段，{@code httpStatus}
 * 桥接父注解（仅「流未开始」时生效，流已提交后状态码已发出不可改）。
 *
 * 示例代码：
 *
 * @formatter:off
 * {@snippet :
 * @RestController
 * @RequestMapping("/v2-test/ndjson")
 * public class NdjsonDemoController {
 *
 *     @NdjsonExceptionResponse(value = SignException.class, code = "B0001", msg = "签名校验失败")
 *     @GetMapping(value = "/export", produces = "application/x-ndjson")
 *     public void export(Writer out) { ... }
 * }
 * }
 * @formatter:on
 *
 * @author Oatelauser
 * @date 2026-08-24
 * @since 2.2
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(NdjsonExceptionResponse.List.class)
@Target({ ElementType.METHOD, ElementType.TYPE })
@ExceptionResponse(protocols = OutputProtocol.NDJSON)
public @interface NdjsonExceptionResponse {

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
     * 仅「流未开始」（响应未提交）时生效；流已开始后状态码已发出，改为在流上补写错误行。
     */
    @AliasFor(annotation = ExceptionResponse.class)
    HttpStatus httpStatus() default HttpStatus.OK;

    /**
     * 自定义错误响应体，桥接父注解 {@link ExceptionResponse#output()}。
     * <p>
     * 必须是 Spring Bean（启动 fail-fast）；产物被 JSON 序列化后作为单行错误记录写出，
     * 序列化失败自动降级标准 {@code SimpleResponse} 错误行（v2.2 兜底）。
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
         * 承载多条 {@link NdjsonExceptionResponse}。
         */
        NdjsonExceptionResponse[] value() default {};
    }

}
