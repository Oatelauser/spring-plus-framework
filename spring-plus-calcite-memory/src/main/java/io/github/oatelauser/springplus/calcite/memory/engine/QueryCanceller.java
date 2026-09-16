package io.github.oatelauser.springplus.calcite.memory.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 查询超时看门狗：在指定超时后取消正在执行的 {@link Statement}。
 *
 * <p>线程模型合规说明：直接构造 {@link ScheduledThreadPoolExecutor}（非 {@code Executors} 工厂），
 * 内部使用 {@link NamedDaemonThreadFactory} 创建守护线程并赋予可排查的有意义名称；
 * {@code removeOnCancelPolicy=true} 确保已取消的看门狗任务从队列移除，避免堆积。
 * 看门狗任务体积极小（仅一次 {@link Statement#cancel()} 调用），且数量受并发查询数约束，
 * 不存在无界队列 OOM 风险。</p>
 */
public final class  QueryCanceller {

    private static final Logger log = LoggerFactory.getLogger(QueryCanceller.class);

    private final ScheduledThreadPoolExecutor scheduler;

    public QueryCanceller() {
        this.scheduler = new ScheduledThreadPoolExecutor(1, new NamedDaemonThreadFactory());
        this.scheduler.setRemoveOnCancelPolicy(true);
    }

    /**
     * 调度一次超时取消。若 {@code timeoutMillis <= 0} 视为不限时，返回空句柄。
     *
     * @param statement     需要被取消的语句
     * @param timeoutMillis 超时毫秒数
     * @return 句柄，调用方应在查询结束后 {@link Handle#close()} 以清除尚未触发的看门狗
     */
    public Handle scheduleCancel(Statement statement, long timeoutMillis) {
        if (timeoutMillis <= 0) {
            return Handle.NOOP;
        }
        ScheduledFuture<?> future = scheduler.schedule(
            () -> safeCancel(statement), timeoutMillis, TimeUnit.MILLISECONDS);
        return new Handle(future);
    }

    private static void safeCancel(Statement statement) {
        try {
            statement.cancel();
        } catch (SQLException e) {
            log.debug("看门狗取消语句失败: {}", e.getMessage());
        }
    }

    /** 关闭看门狗线程池。 */
    public void shutdown() {
        scheduler.shutdownNow();
    }

    /** 看门狗句柄，{@link #close()} 取消尚未触发的取消任务。 */
    public static final class Handle implements AutoCloseable {

        static final Handle NOOP = new Handle(null);

        private final ScheduledFuture<?> future;

        Handle(ScheduledFuture<?> future) {
            this.future = future;
        }

        @Override
        public void close() {
            if (future != null) {
                future.cancel(false);
            }
        }
    }

    /** 命名守护线程工厂，便于 jstack 排查。 */
    private static final class NamedDaemonThreadFactory implements ThreadFactory {

        private final AtomicInteger counter = new AtomicInteger();

        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r, "calcite-memory-canceller-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }
}
