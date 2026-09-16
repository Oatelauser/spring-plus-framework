package io.github.oatelauser.springplus.web.error.sse;

import jakarta.servlet.http.HttpServletRequest;
import org.jspecify.annotations.Nullable;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.Executor;

import static io.github.oatelauser.springplus.web.error.engine.ExceptionOutputEngine.resolveHandlerMethod;

/**
 * {@link SseConnection} 工厂（C 档入口，设计文档 8.3）。
 * <p>
 * 业务 SSE 接口的标准写法：
 * <pre>{@code
 * @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
 * public SseEmitter stream(HttpServletRequest request) {
 *     SseConnection conn = sseConnectionFactory.open(request);
 *     conn.execute(emitter -> {
 *         // 业务代码——抛异常自动转 app-error
 *     });
 *     return conn.emitter();
 * }
 * }</pre>
 *
 * <h3>executor 来源</h3>
 * <p>
 * executor 由业务在 {@link #open} 时<b>显式传入</b>——框架不隐式注入默认池，避免业务流式任务
 * 与其他组件争抢共享池。推荐 {@code Executors.newVirtualThreadPerTaskExecutor()}（项目已启用
 * 虚拟线程，SSE 长连接场景即开即用），或自有 {@code ThreadPoolExecutor}（有界队列 +
 * 明确拒绝策略，阿里规约：禁用 {@code newFixedThreadPool} 等快捷工厂）。
 *
 * @author Oatelauser
 * @date 2026-06-15
 * @since 2.0
 */
public class SseConnectionFactory {

    /**
     * 默认 executor：Spring 的 applicationTaskExecutor（通过 ObjectProvider 延迟解析，避免启动顺序依赖）。
     */
    private final SseExceptionEmitter exceptionEmitter;

    public SseConnectionFactory(SseExceptionEmitter exceptionEmitter) {
        this.exceptionEmitter = exceptionEmitter;
    }

    /**
     * 用指定 executor 打开一个连接（业务可传虚拟线程池等）。
     *
     * @param handlerMethod 当前接口的 HandlerMethod（可 null）
     * @param executor      自定义线程池
     */
    public SseConnection open(@Nullable HandlerMethod handlerMethod, Executor executor) {
        // 0L = 永不超时，由业务通过 emitter.complete()/completeWithError() 显式关闭。
        return new SseConnection(new SseEmitter(0L), executor, handlerMethod, exceptionEmitter);
    }

    public SseConnection open(HttpServletRequest request, Executor executor) {
        HandlerMethod handlerMethod = resolveHandlerMethod(request);
        return open(handlerMethod, executor);
    }

}
