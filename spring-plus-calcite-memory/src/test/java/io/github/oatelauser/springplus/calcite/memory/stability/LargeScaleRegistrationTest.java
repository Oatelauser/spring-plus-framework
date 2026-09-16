package io.github.oatelauser.springplus.calcite.memory.stability;

import io.github.oatelauser.springplus.calcite.memory.engine.MemoryQueryEngine;
import io.github.oatelauser.springplus.calcite.memory.engine.MemoryQuerySession;
import io.github.oatelauser.springplus.calcite.memory.exception.ResourceLimitExceededException;
import io.github.oatelauser.springplus.calcite.memory.result.QueryResult;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * P6-4 容量压测：100,000 行注册无 OOM，超预算注册被资源限制器中止。
 *
 * <p>对应 {@code 08-test-plan.md} 第 7 节「100,000 行注册 | 无 OOM，超预算中止有效」。</p>
 */
@Tag("stress")
class LargeScaleRegistrationTest {

    static final class Order {
        final Long id;
        final Integer amount;
        final String status;

        Order(Long id, Integer amount, String status) {
            this.id = id;
            this.amount = amount;
            this.status = status;
        }
    }

    private List<Order> buildOrders(int n) {
        List<Order> orders = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            orders.add(new Order((long) i, i, (i & 1) == 0 ? "PAID" : "PENDING"));
        }
        return orders;
    }

    /** 100,000 行注册后聚合查询正确，且进程未 OOM（测试本身完成即证明受控）。 */
    @Test
    void register100kRowsNoOom() {
        List<Order> orders = buildOrders(100_000);
        try (MemoryQueryEngine engine = MemoryQueryEngine.create();
             MemoryQuerySession session = engine.openSession()) {
            session.register("orders", orders, Order.class);

            QueryResult count = session.query("SELECT count(*) AS c FROM orders");
            assertEquals(1, count.rowCount());
            assertEquals(100_000L, ((Number) count.row(0).getObject(0)).longValue());

            QueryResult paid = session.query("SELECT count(*) AS c FROM orders WHERE status = 'PAID'");
            assertEquals(50_000L, ((Number) paid.row(0).getObject(0)).longValue());
        }
    }

    /** maxRowsPerTable=1000 时，注册 1001 行应被资源限制器拒绝（超预算中止有效）。 */
    @Test
    void budgetAbortsOverRowLimit() {
        try (MemoryQueryEngine engine = MemoryQueryEngine.create(
                io.github.oatelauser.springplus.calcite.memory.engine.SessionConfig.builder()
                        .maxRowsPerTable(1_000L)
                        .build());
             MemoryQuerySession session = engine.openSession()) {
            List<Order> orders = buildOrders(1_001);
            assertThrows(ResourceLimitExceededException.class,
                    () -> session.register("orders", orders, Order.class));
        }
    }
}
