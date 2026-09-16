package io.github.oatelauser.springplus.calcite.memory.stability;

import io.github.oatelauser.springplus.calcite.memory.engine.MemoryQueryEngine;
import io.github.oatelauser.springplus.calcite.memory.engine.MemoryQuerySession;
import io.github.oatelauser.springplus.calcite.memory.registry.TableRegistry;
import io.github.oatelauser.springplus.calcite.memory.result.QueryResult;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P6-2 并发与版本替换稳定性：多会话并发查询同一应用级快照无串数据；同名表原子替换不累积。
 *
 * <p>对应 {@code 08-test-plan.md} 第 7 节：</p>
 * <ul>
 *   <li>并发 10 Session 查询同一应用级快照 | 结果正确，无串数据</li>
 *   <li>1,000 次应用级表替换 | 旧版本释放</li>
 * </ul>
 */
@Tag("stress")
class ConcurrentSessionStabilityTest {

    static final class Order {
        final Long id;
        final String status;

        Order(Long id, String status) {
            this.id = id;
            this.status = status;
        }
    }

    private List<Order> buildOrders(int n) {
        List<Order> orders = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            orders.add(new Order((long) i, (i & 1) == 0 ? "PAID" : "PENDING"));
        }
        return orders;
    }

    /** 10 个线程各自开 Session 查询同一应用级快照，结果应一致且无串数据。 */
    @Test
    void tenConcurrentSessionsSameSnapshot() throws Exception {
        int threads = 10;
        List<Order> orders = buildOrders(1_000);

        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Long> counts = Collections.synchronizedList(new ArrayList<>());
        List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());

        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                threads, threads, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(threads),
                namedThreadFactory("calcite-concurrent-"),
                new ThreadPoolExecutor.CallerRunsPolicy());
        try (MemoryQueryEngine engine = MemoryQueryEngine.create()) {
            engine.applicationTableRegistry().publishSnapshot("orders", orders, Order.class);

            for (int t = 0; t < threads; t++) {
                pool.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();
                        try (MemoryQuerySession session = engine.openSession()) {
                            QueryResult r = session.query("SELECT count(*) AS c FROM orders WHERE status = 'PAID'");
                            counts.add(((Number) r.row(0).getObject(0)).longValue());
                        }
                    } catch (Throwable e) {
                        errors.add(e);
                    }
                });
            }

            ready.await();
            start.countDown();
            pool.shutdown();
            assertTrue(pool.awaitTermination(60, TimeUnit.SECONDS), "并发任务应在 60s 内完成");
        }

        assertTrue(errors.isEmpty(), () -> "并发查询出现异常: " + errors);
        assertEquals(threads, counts.size(), "每个线程应各产出一个结果");
        assertTrue(counts.stream().allMatch(c -> c == 500L),
                () -> "所有线程应看到相同的 PAID 行数 500（无串数据），实际: " + counts);
    }

    /** 1,000 次同名表原子替换：tableCount 始终为 1（覆盖不累积），最终查询命中末版本。 */
    @Test
    void thousandAtomicTableReplacements() throws Exception {
        try (MemoryQueryEngine engine = MemoryQueryEngine.create()) {
            TableRegistry registry = engine.applicationTableRegistry();
            List<Order> orders = buildOrders(10);

            for (int i = 0; i < 1_000; i++) {
                registry.publishSnapshot("orders", orders, Order.class);
            }

            assertEquals(1, registry.tableCount(),
                    "1,000 次同名替换后表数量应为 1（覆盖而非累积，旧版本不残留）");

            try (MemoryQuerySession session = engine.openSession()) {
                QueryResult r = session.query("SELECT count(*) AS c FROM orders");
                assertEquals(10L, ((Number) r.row(0).getObject(0)).longValue(),
                        "末版本快照应可被新会话查询命中");
            }
        }
    }

    /** 显式线程池所用 ThreadFactory：命名线程便于 jstack 排查，守护线程避免阻塞 JVM 退出。 */
    private static ThreadFactory namedThreadFactory(String prefix) {
        AtomicLong counter = new AtomicLong(0L);
        return r -> {
            Thread t = new Thread(r, prefix + counter.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
    }
}
