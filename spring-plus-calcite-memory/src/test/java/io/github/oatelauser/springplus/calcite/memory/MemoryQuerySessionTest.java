package io.github.oatelauser.springplus.calcite.memory;

import io.github.oatelauser.springplus.calcite.memory.engine.MemoryQueryEngine;
import io.github.oatelauser.springplus.calcite.memory.engine.MemoryQuerySession;
import io.github.oatelauser.springplus.calcite.memory.result.QueryResult;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P1 冒烟测试：验证 POJO List -> 注册 -> SELECT / 投影 / 等值下推 / IS NOT NULL / 聚合 全链路。
 */
class MemoryQuerySessionTest {

    enum OrderStatus { PAID, PENDING, CANCELLED }

    static final class Order {
        final Long id;
        final String orderNo;
        final OrderStatus status;
        final BigDecimal amount;
        final LocalDateTime createdAt;

        Order(Long id, String orderNo, OrderStatus status, BigDecimal amount, LocalDateTime createdAt) {
            this.id = id;
            this.orderNo = orderNo;
            this.status = status;
            this.amount = amount;
            this.createdAt = createdAt;
        }
    }

    @Test
    void selectAllProjectionFilterAndAggregate() {
        List<Order> orders = List.of(
            new Order(1L, "NO-001", OrderStatus.PAID, new BigDecimal("99.50"), LocalDateTime.of(2026, 8, 11, 10, 0)),
            new Order(2L, "NO-002", OrderStatus.PENDING, new BigDecimal("10.00"), LocalDateTime.of(2026, 8, 11, 11, 0)),
            new Order(3L, "NO-003", OrderStatus.PAID, new BigDecimal("250.75"), LocalDateTime.of(2026, 8, 11, 12, 0))
        );

        try (MemoryQueryEngine engine = MemoryQueryEngine.create();
             MemoryQuerySession session = engine.openSession()) {

            session.register("orders", orders, Order.class);

            // 1) 全表：列名 snake_case，行数正确
            QueryResult all = session.query("SELECT * FROM orders");
            assertEquals(3, all.rowCount());
            assertEquals(List.of("id", "order_no", "status", "amount", "created_at"), all.columnNames());

            // 2) 投影 + 等值下推：只返回 status='PAID' 的 id/order_no
            QueryResult paid = session.query("SELECT id, order_no FROM orders WHERE status = 'PAID'");
            assertEquals(2, paid.rowCount());
            assertEquals(List.of("id", "order_no"), paid.columnNames());
            assertEquals(1L, paid.row(0).getLong("id"));
            assertEquals("NO-001", paid.row(0).getString("order_no"));
            assertEquals(3L, paid.row(1).getLong("id"));

            // 3) IS NOT NULL
            QueryResult notNull = session.query("SELECT id FROM orders WHERE id IS NOT NULL");
            assertEquals(3, notNull.rowCount());

            // 4) 聚合 count(*)，按下标取值避免别名大小写问题
            QueryResult count = session.query("SELECT count(*) AS c FROM orders");
            assertEquals(1, count.rowCount());
            assertEquals(3L, ((Number) count.row(0).getObject(0)).longValue());

            // 5) BigDecimal 与等值（按 id 精确定位金额）
            QueryResult amt = session.query("SELECT amount FROM orders WHERE id = 1");
            assertEquals(1, amt.rowCount());
            assertTrue(new BigDecimal("99.50").compareTo(amt.row(0).getBigDecimal("amount")) == 0,
                "BigDecimal 应原样往返");
        }
    }
}
