package io.github.oatelauser.springplus.web.error.mapper;

import io.github.oatelauser.springplus.web.error.descriptor.OutputProtocol;
import jakarta.servlet.http.HttpServletRequest;
import org.jspecify.annotations.Nullable;
import org.springframework.web.method.HandlerMethod;

/**
 * {@link ExceptionMapper} 翻译异常时拿到的上下文（设计文档 6.1）。
 * <p>
 * 相比渲染阶段的 {@code ExceptionRenderContext}，Mapper 阶段还没有 {@link io.github.oatelauser.springplus.web.error.descriptor.ErrorDescriptor}
 * （那正是 Mapper 要产出的东西），所以这里只带「请求运行时信息」。
 * <p>
 * 用 record 表达不可变快照：引擎在协议探测完成后构造一次，Mapper 链上所有 Mapper 共享同一份。
 *
 * @author Oatelauser
 * @date 2026-06-15
 * @since 2.0
 */
public record ExceptionMapperContext(
        /* 当前请求；脱离 HTTP 线程的场景（如 SSE 流内异步异常）可能为 null。 */
        @Nullable HttpServletRequest request,
        /* 命中的 Controller 方法；Filter / HandlerMapping 阶段异常时可能为 null。 */
        @Nullable HandlerMethod handlerMethod,
        /* 当前请求探测出的响应协议，Mapper 可据此做协议过滤（如某个 Mapper 只处理 SSE）。 */
        OutputProtocol protocol
) {

}
