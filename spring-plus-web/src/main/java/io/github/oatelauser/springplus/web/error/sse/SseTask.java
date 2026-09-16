package io.github.oatelauser.springplus.web.error.sse;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE 连接内的业务任务（C 档，设计文档 8.3）。
 * <p>
 * 业务方把「往 emitter 推数据」的逻辑写成 {@link SseTask}，交给 {@link SseConnection#execute} 托管。
 * 任务里抛出的任何异常都会被 {@code SseConnection} 捕获，自动转成 {@code app-error} 事件，
 * 业务代码因此<b>零异常处理样板</b>。
 *
 * <pre>{@code
 * connection.execute(emitter -> {
 *     emitter.send(SseEmitter.event().data(poll()));
 * });
 * }</pre>
 *
 * @author Oatelauser
 * @date 2026-06-15
 * @since 2.0
 */
@FunctionalInterface
public interface SseTask {

    /**
     * @param emitter 当前 SSE 连接的 emitter（由 {@link SseConnection} 托管）
     * @throws Exception 业务可抛任意异常，会被自动转成错误事件
     */
    void run(SseEmitter emitter) throws Exception;

}
