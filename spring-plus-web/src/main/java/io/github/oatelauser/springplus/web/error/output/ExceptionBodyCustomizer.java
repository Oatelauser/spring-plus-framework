package io.github.oatelauser.springplus.web.error.output;

import io.github.oatelauser.springplus.web.error.descriptor.ExceptionContext;
import org.jspecify.annotations.Nullable;

/**
 * 协议层「响应体定制钩子」SPI（v3.0，设计文档 4.4；更名自 {@code ExceptionOutputBase}——
 * 它定制的不是「输出通道」而是「响应体」，名字与职责对齐）。
 * <p>
 * 当业务接口需要按对接方要求返回特殊响应体时（如微信回调必须返回 {@code errcode/errmsg}，
 * 而不是框架默认的 {@code SimpleResponse} 结构），在注解上指定本接口的实现类即可：
 * <pre>{@code
 * @JsonExceptionResponse(value = ServiceException.class, code = "B0001",
 *                        output = WechatErrorCustomizer.class)
 * public SimpleResponse<Void> wechatCallback(...) { ... }
 * }</pre>
 *
 * <h3>与 {@code ExceptionOutputProcessor} 的区别</h3>
 * <p>
 * 两者都管「输出」但职责不同：
 * <ul>
 *   <li>{@code ExceptionOutputProcessor}：协议适配器，每个协议一个实现（JSON / SSE / NDJSON），
 *       框架内置，决定「用 {@code ResponseEntity} 还是 servlet 直写」。</li>
 *   <li>{@code ExceptionBodyCustomizer}（本接口）：响应体钩子，业务方实现，决定「写出的 body
 *       是什么对象」。它在 {@code ExceptionOutputProcessor} 内部被调用——descriptor 上带了一个
 *       bodyCustomizer 时，处理器就用它产出的对象代替默认的 {@code SimpleResponse}。</li>
 * </ul>
 *
 * <h3>启动期绑定 / 运行期零反射</h3>
 * <p>
 * 注解上声明的是 {@code Class<? extends ExceptionBodyCustomizer>}（一个 Class 引用）。
 * 框架在启动期通过 {@code ApplicationContext.getBean(...)} 把它实例化为单例，缓存到
 * {@code ErrorDescriptorTemplate} 里；运行期命中注解时直接方法调用，零反射、零查表。
 * 这条「启动期预热、运行期直调」是 v2.0 以来的性能底线之一（设计文档 1.2）。
 *
 * <h3>返回 null 的语义</h3>
 * <p>
 * 返回 {@code null} 表示「我不处理这次，回退默认响应体」。给业务方一个开关：
 * 同一个 customizer 可以根据 ctx 里的协议/异常类型决定是否接管。
 *
 * @author Oatelauser
 * @date 2026-06-15
 * @since 3.0
 */
public interface ExceptionBodyCustomizer {

    /**
     * 把异常 + 错误描述加工为协议特定的响应载荷。
     * <p>
     * 启动期已绑定到具体注解（{@code ApplicationContext.getBean}），运行期纯方法调用，无反射。
     *
     * @param ctx 渲染上下文（含完整 {@code ErrorDescriptor} + request + handlerMethod + protocol）
     * @return JSON 协议下：要序列化的对象（如 {@code Map} / 自定义 DTO）；<br>
     *         SSE 协议下：要写入 data 字段的对象；<br>
     *         NDJSON 协议下：要写成一行错误记录的对象；<br>
     *         返回 {@code null} 表示「我不处理，回退默认响应体」
     */
    @Nullable
    Object transform(ExceptionContext ctx);

}
