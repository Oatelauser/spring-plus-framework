package io.github.oatelauser.springplus.boot.utils;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * 线程池工具类
 * <p>
 * 提供功能：1.创建命名线程的普通线程池；2.创建 fork/join 线程池；3.线程池的优雅关闭与任务完成等待；
 * 4.fork/join 托管块（ManagedBlocker）封装
 * <p>
 * 通过本类创建的线程池会以 {@link WeakReference} 登记（防止临时线程池内存泄漏），
 * JVM 退出时由关闭钩子统一优雅关闭，参考 log4j2 的 {@code ExecutorServices} 实现
 *
 * @author DearYang
 * @date 2022-04-27
 * @since 1.0
 */
@SuppressWarnings("unused")
public class ExecutorServices {

    private static final Logger log = LoggerFactory.getLogger(ExecutorServices.class);

    private static final int MAXIMUM_FORK_JOIN_CAPACITY = 1024;
    private static final int DEFAULT_SHUTDOWN_TIMEOUT = 10;
    private static final List<WeakReference<ExecutorService>> ALL_THREAD_EXECUTORS = new CopyOnWriteArrayList<>();

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            ALL_THREAD_EXECUTORS.stream().map(WeakReference::get).filter(Objects::nonNull).forEach(ExecutorServices::shutdown);
        }));
    }

    /**
     * 创建固定大小的普通线程池
     * <p>
     * 核心线程数与最大线程数相同，使用容量 50240 的有界队列，队列满时按
     * {@link ThreadPoolExecutor.AbortPolicy} 拒绝并抛出 {@link RejectedExecutionException}；
     * 创建出的线程池会登记到 JVM 关闭钩子中统一优雅关闭
     *
     * @param size             线程数
     * @param threadNamePrefix 线程名前缀
     * @return 线程池
     */
    @SuppressWarnings("resource")
    public static ExecutorService createNormalThreadPool(int size, String threadNamePrefix) {
        ThreadFactory threadFactory = new NamedThreadFactory(threadNamePrefix, new ThreadUnCaughtExceptionHandler());
        ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(size, size, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(50240), threadFactory, new ThreadPoolExecutor.AbortPolicy());
        return buildExecutorService(threadPoolExecutor);
    }

    /**
     * 创建{@link ForkJoinTask} 的线程池，提供并行计算任务
     *
     * @param size             一定是2的n次方，并且最大1024
     * @param threadNamePrefix 线程名
     * @return fork/join线程池
     */
    @SuppressWarnings("resource")
    public static ForkJoinPool createForkJoinTaskThreadPool(int size, String threadNamePrefix) {
        size = tableSizeFor(size, MAXIMUM_FORK_JOIN_CAPACITY);
        NamedForkJoinWorkerThreadFactory factory = new NamedForkJoinWorkerThreadFactory(threadNamePrefix);
        ForkJoinPool forkJoinPool = new ForkJoinPool(size, factory, new ThreadUnCaughtExceptionHandler(), false);
        return buildExecutorService(forkJoinPool);
    }

    /**
     * 优雅关闭给定的 {@link ExecutorService}：停止接收新任务，等待已提交任务执行完毕，
     * 超时后强制取消正在执行的任务，超时时间默认 10 秒
     *
     * @param executorService 待关闭的线程池
     * @return true-线程池已终止；false-超时前未能终止
     */
    public static boolean shutdown(final ExecutorService executorService) {
        return shutdown(DEFAULT_SHUTDOWN_TIMEOUT, TimeUnit.SECONDS, "", executorService);
    }

    /**
     * 优雅关闭给定的 {@link ExecutorService}：先停止接收新任务，再等待已提交任务执行完毕，
     * 超时后调用 {@code shutdownNow()} 强制取消正在执行的任务并再次等待
     * <p>
     * 超时时间为 0 时，只停止接收新任务、不等待已有任务执行完毕
     *
     * @param executorService 待关闭的线程池
     * @param timeout         最大等待时间，0 表示不等待
     * @param unit            时间单位
     * @param source          日志中标识调用来源的字符串
     * @return true-线程池已终止；false-超时前未能终止
     * @throws IllegalArgumentException timeout 为负数或 unit 为 null
     */
    public static boolean shutdown(long timeout, TimeUnit unit, String source, ExecutorService executorService) {
        if (executorService == null || executorService.isTerminated()) {
            return true;
        }
        executorService.shutdown(); // 停止接收新任务
        if (timeout < 0 || unit == null) {
            throw new IllegalArgumentException(String.format("%s can't shutdown %s when timeout = %,d and timeUnit = %s.",
                    source, executorService, timeout, unit));
        }
        if (timeout > 0) {
            try {
                // 等待已提交任务执行完毕
                if (!executorService.awaitTermination(timeout, unit)) {
                    executorService.shutdownNow(); // 取消正在执行的任务
                    // 等待任务响应取消
                    if (!executorService.awaitTermination(timeout, unit)) {
                        log.error("{} pool {} did not terminate after {} {}", source, executorService, timeout, unit);
                    }
                    return false;
                }
            } catch (final InterruptedException ie) {
                // 当前线程被中断时（重新）取消任务
                executorService.shutdownNow();
                // 保留中断状态
                Thread.currentThread().interrupt();
            }
        } else {
            executorService.shutdown();
        }
        return true;
    }

    /**
     * 加入{@code allExecutors}，在 SpringBoot 优雅关闭关闭的时候，在 Servlet 线程池关闭后，遍历检查线程池集合，保证这些线程池做完所有任务
     * 同时防止对于临时线程池的内存泄漏，设置为 {@link WeakReference}
     *
     * @param executorService 线程池
     * @param <T>             线程池对象
     * @return 线程池
     */
    private static <T extends ExecutorService> T buildExecutorService(T executorService) {
        ALL_THREAD_EXECUTORS.add(new WeakReference<>(executorService));
        return executorService;
    }

    /**
     * 计算初始值，该数字会转换为2的n次方，并且会小于等于最大值
     * <p>
     * 参考的{@code java.util.HashMap#tableSizeFor(int)}算法
     *
     * @param cap     初始值
     * @param maximum 最大值
     * @return 符合规则的初始值
     */
    public static int tableSizeFor(int cap, int maximum) {
        int n = cap - 1;
        n |= n >>> 1;
        n |= n >>> 2;
        n |= n >>> 4;
        n |= n >>> 8;
        n |= n >>> 16;
        return (n < 0) ? 1 : (n >= maximum) ? maximum : n + 1;
    }

    /**
     * 判断线程池任务是否执行完成
     * <p>
     * 1.{@link ThreadPoolExecutor} 只需要判断活跃线程数
     * 2.{@link ForkJoinPool} 需要判断：活跃线程数、running的线程数、任务队列、提交任务队列
     *
     * @param executorService 线程池
     * @return true-没有任务执行，false-有任务在执行
     */
    public static boolean isCompleted(ExecutorService executorService) {
        if (executorService instanceof ThreadPoolExecutor threadPoolExecutor) {
            return threadPoolExecutor.getActiveCount() == 0;
        } else if (executorService instanceof ForkJoinPool forkJoinPool) {
            return forkJoinPool.getActiveThreadCount() == 0 && forkJoinPool.getRunningThreadCount() == 0
                    && forkJoinPool.getQueuedTaskCount() == 0 && forkJoinPool.getQueuedSubmissionCount() == 0;
        }
        return true;
    }

    /**
     * 等待所有的线程池任务执行完成
     * <p>
     * 采用随机打乱线程池的方式，去判断线程池是否执行完成，如果三次都成功则认为是线程池没有任务执行
     *
     * @param timeout          等待超时时间，时间单位秒，如果是<0则无超时时间
     * @param executorServices 线程池集合
     */
    public static void await(int timeout, List<ExecutorService> executorServices) {
        for (int i = 0, j = 0; i < 3; j++) {
            if (j == timeout) {
                break;
            }

            Collections.shuffle(executorServices);
            if (executorServices.stream().allMatch(ExecutorServices::isCompleted)) {
                i++;
                log.info("all threads pools are completed, i: {}", i);
                continue;
            }

            i = 0;
            log.info("not all threads pools are completed, wait for 1s");
            try {
                TimeUnit.SECONDS.sleep(1);
            } catch (InterruptedException ignored) {
            }
        }
    }

    /**
     * 无超时地等待所有的线程池任务执行完成
     *
     * @param executorServices 线程池集合
     * @see #await(int, List)
     */
    public static void await(List<ExecutorService> executorServices) {
        await(-1, executorServices);
    }

    /**
     * 自定义名称的 fork/join 工作线程
     */
    public static class NamedForkJoinWorkerThread extends ForkJoinWorkerThread {
        protected NamedForkJoinWorkerThread(String threadName, ForkJoinPool pool) {
            super(pool);
            setName(threadName);
        }
    }

    /**
     * 为 {@link ForkJoinPool} 创建 {@link NamedForkJoinWorkerThread} 的线程工厂，
     * 按「前缀-序号」为工作线程命名，便于线程 dump 排查
     */
    public static final class NamedForkJoinWorkerThreadFactory implements ForkJoinPool.ForkJoinWorkerThreadFactory {
        private final String threadPrefixName;
        private final AtomicInteger index = new AtomicInteger(0);

        public NamedForkJoinWorkerThreadFactory(String threadPrefixName) {
            this.threadPrefixName = threadPrefixName;
        }

        @Override
        public ForkJoinWorkerThread newThread(ForkJoinPool pool) {
            return new NamedForkJoinWorkerThread(threadPrefixName + "-" + index.getAndIncrement(), pool);
        }
    }

    /**
     * 线程未捕获异常处理器：记录错误日志
     */
    public static final class ThreadUnCaughtExceptionHandler implements Thread.UncaughtExceptionHandler {
        @Override
        public void uncaughtException(Thread t, Throwable e) {
            log.error("线程【{}】发生异常", t.getName(), e);
        }
    }

    /**
     * 命名的线程工厂，用于为线程池中的线程自定义名称和异常处理器
     */
    public static class NamedThreadFactory implements ThreadFactory {
        private final String namePrefix;
        private final boolean isDaemon;
        private final Thread.UncaughtExceptionHandler exceptionHandler;
        private final AtomicInteger threadNumber = new AtomicInteger(1);

        /**
         * 构造非守护线程的线程工厂
         *
         * @param threadNamePrefix 线程名称前缀，例如 "my-pool-"
         * @param exceptionHandler 自定义未捕获异常处理器
         */
        public NamedThreadFactory(String threadNamePrefix, Thread.UncaughtExceptionHandler exceptionHandler) {
            this(threadNamePrefix, exceptionHandler, false);
        }

        /**
         * 全参数构造方法（支持设置是否为守护线程）
         */
        public NamedThreadFactory(String threadNamePrefix, Thread.UncaughtExceptionHandler exceptionHandler, boolean isDaemon) {
            // 前缀统一以 "-" 结尾，保证线程名形如 my-pool-1
            if (threadNamePrefix != null && !threadNamePrefix.endsWith("-")) {
                this.namePrefix = threadNamePrefix + "-";
            } else {
                this.namePrefix = threadNamePrefix;
            }
            this.exceptionHandler = exceptionHandler;
            this.isDaemon = isDaemon;
        }

        @Override
        public Thread newThread(@NonNull Runnable runnable) {
            // 组装线程名称，例如: my-pool-1, my-pool-2
            String threadName = (namePrefix != null ? namePrefix : "pool-") + threadNumber.getAndIncrement();
            Thread thread = new Thread(runnable, threadName);

            thread.setDaemon(isDaemon);

            // 重置为默认优先级，避免继承父线程的特殊优先级
            if (thread.getPriority() != Thread.NORM_PRIORITY) {
                thread.setPriority(Thread.NORM_PRIORITY);
            }

            if (exceptionHandler != null) {
                thread.setUncaughtExceptionHandler(exceptionHandler);
            }
            return thread;
        }
    }

    /**
     * fork-join线程中如果任务阻塞， 会自动添加线程
     */
    public static class ManagedBlocks {

        /**
         * 在 {@link ForkJoinPool.ManagedBlocker} 中执行供给函数
         * <p>
         * 供给函数可能长时间阻塞时，通过托管块通知当前 fork/join 池临时补充工作线程，
         * 避免池内工作线程被阻塞耗尽导致并行度下降
         *
         * @param supplier 供给函数
         * @param <T>      结果类型
         * @return 供给函数的执行结果
         * @throws IllegalStateException 执行过程中线程被中断
         */
        public static <T> T callInManagedBlock(final Supplier<T> supplier) {
            final SupplierManagedBlock<T> managedBlock = new SupplierManagedBlock<>(supplier);
            try {
                ForkJoinPool.managedBlock(managedBlock);
            } catch (InterruptedException e) {
                throw new IllegalStateException(e);
            }
            return managedBlock.result;
        }

        private static class SupplierManagedBlock<T> implements ForkJoinPool.ManagedBlocker {
            private final Supplier<T> supplier;
            private T result;
            private boolean done = false;

            private SupplierManagedBlock(final Supplier<T> supplier) {
                this.supplier = supplier;
            }

            @Override
            public boolean block() {
                result = supplier.get();
                done = true;
                return true;
            }

            @Override
            public boolean isReleasable() {
                return done;
            }
        }
    }

}
