package io.github.oatelauser.springplus.web.error.annotation;

import io.github.oatelauser.springplus.web.error.descriptor.LogStackPolicy;
import io.github.oatelauser.springplus.web.error.descriptor.OutputProtocol;
import io.github.oatelauser.springplus.web.error.output.ExceptionBodyCustomizer;
import org.springframework.http.HttpStatus;

import java.lang.annotation.*;

import static io.github.oatelauser.springplus.web.response.ServerStatus.SERVER_INTERNAL_CODE;

/**
 * 接口返回的默认异常响应（v3.0：异常响应注解家族的<b>父注解</b>）。
 * <p>
 * v3.0 的注解家族采用 Spring 的「父注解 + 协议派生注解」组合模型（{@code @RequestMapping} 与
 * {@code @GetMapping} 同款）：
 * <ul>
 *   <li>本注解承载<b>协议无关</b>的全部通用字段（code / msg / httpStatus / output / logPolicy ...）；</li>
 *   <li>{@code @JsonExceptionResponse} / {@code @SseExceptionResponse} / {@code @NdjsonExceptionResponse}
 *       通过元注解 {@code @ExceptionResponse(protocols = ...)} 锁死协议，公共字段经
 *       {@code @AliasFor} 桥接回本注解；</li>
 *   <li>框架启动期用<b>元注解反查</b>（{@code ExceptionAnnotationUtils}）识别家族成员——
 *       业务自定义派生注解只需同样挂元注解 + {@code @AliasFor} 桥接，框架零改动即识别
 *       （对齐 Spring 发现 {@code @Component} 派生注解的机制，无需预注册）。</li>
 * </ul>
 *
 * 示例代码：
 *
 * @formatter:off
 * {@snippet :
 * @RestController
 * @RequestMapping("/api/user")
 * // ========== 类级别 ==========
 * @ExceptionResponse(value = DataAccessException.class, code = "C0200", msg = "数据库异常")
 * public class UserController {
 *
 *     // ========== 方法级别 ==========
 *
 *     @PostMapping
 *     @ExceptionResponse(value = DuplicateKeyException.class, code = "B0102", msg = "用户名已存在")
 *     @ExceptionResponse(value = IllegalArgumentException.class, showException = true)
 *     public SimpleResponse<Void> createUser(@RequestBody UserDTO dto) {
 *         userService.create(dto);
 *         return SimpleResponse.ok();
 *     }
 *
 *     // ========== 协议无关的真实 HTTP 状态码（v3.0 上移到父注解） ==========
 *
 *     @GetMapping("/{id}")
 *     @ExceptionResponse(value = ResourceNotFoundException.class, code = "A0404",
 *                        msg = "资源不存在", httpStatus = HttpStatus.NOT_FOUND)
 *     public SimpleResponse<UserView> getUser(@PathVariable Long id) { ... }
 *
 *     // ========== 在异常类上定义 ==========
 * }
 *
 * @ExceptionResponse(code = "B0100", msg = "用户不存在")
 * public class UserNotFoundException extends RuntimeException {
 *     public UserNotFoundException(String message) {
 *         super(message);
 *     }
 * }
 * }
 * @formatter:on
 *
 * @author <a href="mailto:545896770@qq.com">DearYang</a>
 * @date 2023-04-07
 * @since 1.0
 */
@Inherited
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(ExceptionResponse.List.class)
@Target({ ElementType.METHOD, ElementType.TYPE })
public @interface ExceptionResponse {

    /**
     * 处理的兜底异常
     */
    Class<? extends Throwable>[] value() default Exception.class;

    /**
     * 响应code
     */
    String code() default SERVER_INTERNAL_CODE;

    /**
     * 响应异常信息
     */
    String msg() default "";

    /**
     * 是否把异常信息作为msg
     */
    boolean showException() default false;

    /**
     * 优先级（数值越小优先级越高）
     * <p>
     * 默认优先级规则：
     * <ul>
     *   <li>方法级注解：100</li>
     *   <li>异常类级注解：200</li>
     *   <li>控制器类级注解：300</li>
     * </ul>
     * 可通过此属性自定义优先级
     */
    int order() default 0;

    /**
     * 协议无关的 HTTP 状态码意图（v3.0 上移自派生注解——原
     * {@code @JsonExceptionResponse#httpStatus()} / {@code @NdjsonExceptionResponse#httpStatus()} /
     * {@code @SseExceptionResponse#handshakeStatus()} 三者同义，统一收编于此）。
     * <p>
     * 语义：希望这次错误响应呈现的真实 HTTP 状态码。各协议的生效边界：
     * <ul>
     *   <li><b>JSON</b>：写入 {@code ResponseEntity} 的状态码；</li>
     *   <li><b>NDJSON</b>：仅「流未开始」（响应未提交）时生效——流已开始后状态码已发出，不可改；</li>
     *   <li><b>SSE</b>：仅握手期生效——连接建立后客户端（EventSource）只在 HTTP 200 下收事件，
     *       非 200 会导致客户端直接 onerror 而收不到错误事件，慎用；</li>
     *   <li><b>所有协议缺省 {@link HttpStatus#OK}</b>：保持「业务码承载错误、HTTP 200」的兼容风格。</li>
     * </ul>
     * 启动期固化进 {@code ErrorDescriptorTemplate.statusIntent}（OK 归一化为 null），运行期由各协议处理器读取。
     *
     * @since 3.0
     */
    HttpStatus httpStatus() default HttpStatus.OK;

    /**
     * <b>派生注解专用字段，业务方在 {@link ExceptionResponse} 上直接设置会启动失败。</b>
     * <p>
     * 此字段的存在意义是让派生注解（{@code @JsonExceptionResponse} / {@code @SseExceptionResponse} /
     * {@code @NdjsonExceptionResponse} 及业务自定义派生注解）通过元注解
     * {@code @ExceptionResponse(protocols = HTTP_JSON / ...)} 声明自己的协议归属。
     * 启动期 {@code AnnotationToTemplateConverter} 据此把派生注解固化为单协议规则，运行期 O(1) 过滤。
     *
     * <h3>为什么直接设置会启动失败（v3.0 行为）</h3>
     * <p>
     * v2.x 时代直接设置会被启动期扫描<b>静默跳过</b>（当时的扫描用 {@code protocols.length > 0}
     * 区分「派生注解合并视图」与「普通注解」，业务直设恰好落进前者分支被误杀）。静默失效是最坏的
     * 失效形态——注解贴了、不生效、没人知道。v3.0 扫描已改为「元注解反查收集原始注解实例」，
     * 不再需要该过滤技巧；直设 {@code protocols} 属于无意义配置，改为
     * {@code HandlerExceptionAnnotationProcessor} 启动期抛 {@link IllegalStateException} 快速失败。
     *
     * <h3>需要协议过滤时的正确写法</h3>
     * <ul>
     *   <li>需要 JSON 协议过滤：用 {@code @JsonExceptionResponse}。</li>
     *   <li>需要 SSE 协议过滤：用 {@code @SseExceptionResponse}（还能设置 {@code event} / {@code retry}）。</li>
     *   <li>需要 NDJSON 协议过滤：用 {@code @NdjsonExceptionResponse}。</li>
     *   <li>全协议通用：直接 {@code @ExceptionResponse(...)}，不设置本字段。</li>
     * </ul>
     *
     * @since 2.0
     */
    OutputProtocol[] protocols() default {};

    /**
     * 自定义错误响应体（v3.0 更名：原 {@code ExceptionOutputBase}）。
     * <p>
     * 默认值 {@link ExceptionBodyCustomizer}.class 本身作为「未指定」哨兵——因为 Java 注解成员
     * 不能用 null 做默认值，框架用「接口自身的 Class」表示「业务没指定，走默认响应体」。
     * 启动期由 {@code AnnotationToTemplateConverter} 判断：值等于哨兵则 bodyCustomizer = null，
     * 否则通过 {@code ApplicationContext.getBean(...)} 实例化并缓存到模板，运行期纯方法调用、零反射。
     * <p>
     * <b>必须是 Spring Bean</b>（v3.0 唯一业务扩展点，强制 Bean）：未注册（没标 {@code @Component} /
     * 没有 {@code @Bean} 方法）直接导致应用启动失败（fail-fast）。
     * <p>
     * 典型场景：对接微信回调必须返回 {@code errcode/errmsg} 而非 {@code SimpleResponse} 结构。
     *
     * @since 2.0
     */
    Class<? extends ExceptionBodyCustomizer> output() default ExceptionBodyCustomizer.class;

    /**
     * 日志堆栈策略。
     * <p>
     * {@link LogStackPolicy#DEFAULT}（默认）表示遵循框架静态分类表 + 全局配置，兼容 v1.0 行为。
     * 业务可显式设为 {@link LogStackPolicy#ALWAYS} / {@link LogStackPolicy#NEVER} 强制覆盖，
     * 是日志四级决策链的第一级（设计文档 5.3）。
     *
     * @since 2.0
     */
    LogStackPolicy logPolicy() default LogStackPolicy.DEFAULT;

    /**
     * {@link Repeatable} 容器：业务方在同一方法 / 类上重复贴 {@link ExceptionResponse} 时，
     * 编译器会自动包成本容器，无需手写。命名为 {@code List} 而非 {@code ExceptionResponses} 是为了：
     * <ul>
     *   <li>命名空间收敛——容器与父注解共享同一个类名前缀，IDE 自动补全更直观（输 {@code @ExceptionResponse}
     *       即可看到 {@code List}）。</li>
     *   <li>少一个外部 public 类型，降低业务方对框架 API 的认知成本。</li>
     * </ul>
     */
    @Inherited
    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ ElementType.METHOD, ElementType.TYPE })
    @interface List {
        /**
         * 承载多条 {@link ExceptionResponse}。
         */
        ExceptionResponse[] value() default {};
    }

}
