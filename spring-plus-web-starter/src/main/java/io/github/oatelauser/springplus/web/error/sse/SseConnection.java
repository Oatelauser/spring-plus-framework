package io.github.oatelauser.springplus.web.error.sse;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.Executor;

/**
 * SSE 连接封装（C 档，设计文档 8.3）。
 * <p>
 * 把「emitter + executor + 异常处理」打包成一个连接对象，业务只需
 * {@code connection.execute(emitter -> {...})}，<b>零异常处理样板</b>——任务里抛的异常
 * 会被自动捕获，委托给 {@link SseExceptionEmitter} 转成 {@code app-error} 事件。
 *
 * <h3>生命周期</h3>
 * <ol>
 *   <li>由 {@link SseConnectionFactory#open} 创建，内部 new 一个 {@link SseEmitter}（永不超时）。</li>
 *   <li>业务多次调 {@link #execute} 推数据，每次任务在 {@link #executor} 上异步跑。</li>
 *   <li>任务抛异常 → {@link SseExceptionEmitter#completeWithError} → 写错误事件 + 关闭。</li>
 * </ol>
 *
 * <h3>executor 来源</h3>
 * <p>
 * 由 {@link SseConnectionFactory#open} 转递业务<b>显式传入</b>的 executor（推荐
 * {@code Executors.newVirtualThreadPerTaskExecutor()}，SSE 长连接场景即开即用）。
 * 本类只透传不持有生命周期——业务传自有池时由业务负责关闭。
 *
 * @author Oatelauser
 * @date 2026-06-15
 * @since 2.0
 */
public final class SseConnection {

    private static final Logger LOG = LoggerFactory.getLogger(SseConnection.class);

    private final SseEmitter emitter;
    private final Executor executor;
    private final HandlerMethod handlerMethod;
    private final SseExceptionEmitter exceptionEmitter;

    SseConnection(SseEmitter emitter, Executor executor,
            @Nullable HandlerMethod handlerMethod,
            SseExceptionEmitter exceptionEmitter) {
        this.emitter = emitter;
        this.executor = executor;
        this.handlerMethod = handlerMethod;
        this.exceptionEmitter = exceptionEmitter;
    }

    /**
     * 当前连接的 emitter，Controller 直接 return 给 Spring MVC。
     */
    public SseEmitter emitter() {
        return emitter;
    }

    /**
     * 在 {@link #executor} 上异步执行一个 SSE 任务；任务抛异常自动转 {@code app-error}。
     *
     * @param task 业务任务（往 emitter 推数据）
     */
    public void execute(SseTask task) {
        executor.execute(() -> {
            try {
                task.run(emitter);
            } catch (Exception ex) {
                // 业务异常：委托 B 档工具写 app-error 事件 + 关闭连接。
                exceptionEmitter.completeWithError(emitter, ex, handlerMethod);
            } catch (Throwable error) {
                // JVM 级 Error（OOM / StackOverflow 等）：不再尝试写错误事件——序列化本身要分配
                // 内存，濒死的 JVM 上大概率二次失败且丢掉根因；ERROR 落盘后原样重抛，
                // 交给执行器线程的未捕获异常语义收尾（v2.1 边界加固）。
                LOG.error("SSE 任务抛出 JVM 级 Error，放弃错误事件写入并重抛", error);
                throw error;
            }
        });
    }
}
