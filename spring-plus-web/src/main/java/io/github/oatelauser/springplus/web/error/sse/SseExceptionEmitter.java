package io.github.oatelauser.springplus.web.error.sse;

import io.github.oatelauser.springplus.web.error.descriptor.ErrorDescriptor;
import io.github.oatelauser.springplus.web.error.descriptor.ExceptionContext;
import io.github.oatelauser.springplus.web.error.descriptor.OutputProtocol;
import io.github.oatelauser.springplus.web.error.engine.ExceptionOutputEngine;
import io.github.oatelauser.springplus.web.error.output.SseExceptionProcessor;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static io.github.oatelauser.springplus.web.error.engine.ExceptionOutputEngine.resolveHandlerMethod;

/**
 * SSE 流内异常工具（B 档，设计文档 8.2）。
 * <p>
 * 业务在异步线程内 {@code emitter.send(...)} 之后抛异常时，response 已提交，{@code @RestControllerAdvice}
 * 无法再接管——此时业务在自己的 try/catch 里调用 {@link #completeWithError}，往<b>已有的</b> emitter
 * 写一个 {@code app-error} 事件后关闭连接。
 *
 * <h3>三档分工里的位置</h3>
 * <pre>
 *   A 档（握手期）   → @RestControllerAdvice 自动接管（ExceptionOutputEngine.dispatch）
 *   B 档（流内工具） → 本类 completeWithError（业务 try/catch 调用）   ← 这里
 *   C 档（连接封装） → SseConnection.execute（内部委托给本类）
 * </pre>
 *
 * <h3>复用引擎的解析与日志</h3>
 * <p>
 * 描述解析走 {@link ExceptionOutputEngine#resolve}（与方法/类注解、Mapper 链完全一致），
 * 日志走 {@link ExceptionOutputEngine#log}（与 A 档同一套日志策略），写帧走
 * {@link SseExceptionProcessor#sendErrorEvent}（与 A 档同一段渲染逻辑）。三档行为完全一致，
 * 只是触发位置不同。
 *
 * @author Oatelauser
 * @date 2026-06-15
 * @since 2.0
 */
public class SseExceptionEmitter {

    private static final Logger LOG = LoggerFactory.getLogger(SseExceptionEmitter.class);

    private final ExceptionOutputEngine outputEngine;
    private final SseExceptionProcessor exceptionProcessor;

    public SseExceptionEmitter(ExceptionOutputEngine outputEngine, SseExceptionProcessor exceptionProcessor) {
        this.outputEngine = outputEngine;
        this.exceptionProcessor = exceptionProcessor;
    }


    public void completeWithError(SseEmitter emitter, Throwable ex, HttpServletRequest request) {
        HandlerMethod handlerMethod = resolveHandlerMethod(request);
        this.completeWithError(emitter, ex, handlerMethod);
    }

    /**
     * 把异常转成 {@code app-error} 事件写到已有 emitter，然后关闭连接。
     * <p>
     * 业务用法：
     * <pre>{@code
     * executor.execute(() -> {
     *     try {
     *         emitter.send(...);
     *     } catch (Throwable ex) {
     *         sseExceptionEmitter.completeWithError(emitter, ex, handlerMethod);
     *     }
     * });
     * }</pre>
     *
     * @param emitter       业务已有的 emitter
     * @param ex            异步线程内抛出的异常
     * @param handlerMethod 当前接口的方法对象
     */
    public void completeWithError(SseEmitter emitter, Throwable ex, HandlerMethod handlerMethod) {
        try {
            // 解析描述：协议固定 SSE；无 HTTP 请求（异步线程），req 传 null。
            ErrorDescriptor descriptor = this.outputEngine.resolve(ex, null, null, handlerMethod, OutputProtocol.HTTP_SSE);
            // 流内异常也要落盘日志。
            this.outputEngine.log(descriptor, null, handlerMethod, OutputProtocol.HTTP_SSE);
            ExceptionContext ctx = this.outputEngine.buildContext(descriptor, null, handlerMethod, OutputProtocol.HTTP_SSE);
            exceptionProcessor.sendErrorEvent(emitter, descriptor, ctx);
            emitter.complete();
        } catch (Throwable sendEx) {
            // 写帧本身失败（客户端已断开等）：按失败完成 emitter，释放句柄。
            LOG.warn("SSE 错误事件写入失败", sendEx);
            emitter.completeWithError(sendEx);
        }
    }

}
