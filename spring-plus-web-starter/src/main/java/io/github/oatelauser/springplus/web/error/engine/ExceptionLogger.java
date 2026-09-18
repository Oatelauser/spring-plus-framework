package io.github.oatelauser.springplus.web.error.engine;

import io.github.oatelauser.springplus.web.error.descriptor.ErrorDescriptor;
import io.github.oatelauser.springplus.web.error.descriptor.OutputProtocol;
import jakarta.servlet.http.HttpServletRequest;
import org.jspecify.annotations.Nullable;
import org.springframework.web.method.HandlerMethod;

/**
 * 异常日志器 SPI（v3.0，设计文档 4.4）。
 * <p>
 * 引擎在 {@link ErrorDescriptor} 解析完成、渲染<b>之前</b>调用 {@link #log}，把异常落盘。
 * 把「打日志」从每个 {@code @ExceptionHandler} 方法里手写，收口到这一个入口。
 *
 * <h3>决策来源（v3.0 线性四级链，设计 5.3）</h3>
 * <p>
 * 实现方需按以下顺序决定日志级别和是否打堆栈（先命中先停）：
 * <pre>
 *   1. descriptor.logPolicy 显式 ALWAYS / NEVER  → 直接拍板
 *   2. ServiceException.withStack()              → 强制打堆栈（业务方显式要求）
 *   3. 表兜底条目（未知异常）且 printStackForUnknown=false → 不打堆栈
 *   4. 按静态分类表（LogPolicyTable）结论
 * </pre>
 * 默认实现 {@code DefaultExceptionLogger} 已内置该决策；业务方注册自己的
 * {@code @Bean ExceptionLogger} 可整体替换日志行为（接入统一日志平台、脱敏、采样）。
 *
 * @author Oatelauser
 * @date 2026-06-15
 * @since 2.0
 */
public interface ExceptionLogger {

    /**
     * 记录一次异常日志。
     *
     * @param descriptor    已就绪的错误描述（含 code/message/exception/logPolicy）
     * @param request       当前请求；脱离 HTTP 线程的场景（SSE 流内异步）可能为 null
     * @param handlerMethod 命中的 Controller 方法；Filter / HandlerMapping 阶段异常时可能为 null
     * @param protocol      当前请求探测出的响应协议
     */
    void log(ErrorDescriptor descriptor, @Nullable HttpServletRequest request,
            @Nullable HandlerMethod handlerMethod, OutputProtocol protocol);

}
