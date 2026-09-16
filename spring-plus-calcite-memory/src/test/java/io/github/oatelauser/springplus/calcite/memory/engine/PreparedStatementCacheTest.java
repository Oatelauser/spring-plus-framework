package io.github.oatelauser.springplus.calcite.memory.engine;

import io.github.oatelauser.springplus.calcite.memory.exception.CalciteMemoryException;
import io.github.oatelauser.springplus.calcite.memory.result.QueryResult;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 语句模板缓存集成测试：透明参数化（Druid 风格）下的正确性与缓存行为。
 * 覆盖：同型不同值共享语句、IN 列表、结构位置保留、schema 变更失效、LRU 上限、失败回退。
 */
class PreparedStatementCacheTest {

    static final class Order {
        final Long id;
        final String status;
        final Integer amount;

        Order(Long id, String status, Integer amount) {
            this.id = id;
            this.status = status;
            this.amount = amount;
        }
    }

    private MemoryQueryEngine engine;
    private MemoryQuerySession session;

    @BeforeEach
    void setUp() {
        engine = MemoryQueryEngine.create();
        session = engine.openSession();
        registerDefaultOrders();
    }

    @AfterEach
    void tearDown() {
        session.close();
        engine.close();
    }

    private void registerDefaultOrders() {
        session.register("orders", List.of(
                new Order(1L, "PAID", 100),
                new Order(2L, "PAID", 200),
                new Order(3L, "PENDING", 50)), Order.class);
    }

    @Test
    void sameTemplateDifferentValuesShareOneStatement() {
        QueryResult paid = session.query("SELECT id FROM orders WHERE status = 'PAID'");
        assertEquals(2, paid.rowCount());

        // 故意用不同空白格式：结构相同即归并同一模板
        QueryResult pending = session.query("SELECT id FROM orders WHERE status='PENDING'");
        assertEquals(1, pending.rowCount());
        assertEquals(3L, pending.row(0).getLong("id"));

        QueryResult none = session.query("SELECT id FROM orders WHERE status = 'NOT_EXIST'");
        assertEquals(0, none.rowCount());

        assertEquals(1, session.cachedStatementCount(), "同型不同值应共享一条缓存语句");
    }

    @Test
    void numericAndInListFiltersCorrect() {
        QueryResult big = session.query("SELECT id FROM orders WHERE amount > 80");
        assertEquals(2, big.rowCount());

        QueryResult one = session.query("SELECT id FROM orders WHERE id IN (1)");
        assertEquals(1, one.rowCount());

        // IN 列表长度不同 -> 不同模板，各自缓存
        QueryResult two = session.query("SELECT id FROM orders WHERE id IN (1, 2)");
        assertEquals(2, two.rowCount());
        assertTrue(session.cachedStatementCount() >= 2, "不同长度 IN 列表应是不同模板");
    }

    @Test
    void limitAndOrderByOrdinalStillWork() {
        QueryResult top2 = session.query("SELECT id FROM orders ORDER BY 1 LIMIT 2");
        assertEquals(2, top2.rowCount());
        assertEquals(1L, top2.row(0).getLong("id"));

        QueryResult top1 = session.query("SELECT id FROM orders ORDER BY 1 LIMIT 1");
        assertEquals(1, top1.rowCount());
    }

    @Test
    void schemaChangeInvalidatesCachedStatements() {
        QueryResult before = session.query("SELECT count(*) AS c FROM orders WHERE status = 'PAID'");
        assertEquals(2L, ((Number) before.row(0).getObject(0)).longValue());

        // 覆盖同名表：数据全变，缓存计划引用旧表对象，必须失效重建
        session.register("orders", List.of(new Order(9L, "PENDING", 1)), Order.class);

        QueryResult after = session.query("SELECT count(*) AS c FROM orders WHERE status = 'PAID'");
        assertEquals(0L, ((Number) after.row(0).getObject(0)).longValue(), "重新注册后必须看到新数据");
    }

    @Test
    void dropTableInvalidatesCachedStatements() {
        session.query("SELECT id FROM orders WHERE status = 'PAID'");
        assertTrue(session.dropTable("orders"));
        assertThrows(CalciteMemoryException.class,
                () -> session.query("SELECT id FROM orders WHERE status = 'PAID'"));
    }

    @Test
    void lruBoundEvictsLeastRecentlyUsed() {
        try (MemoryQueryEngine boundedEngine = MemoryQueryEngine.create();
             MemoryQuerySession bounded = boundedEngine.openSession(
                     SessionConfig.builder().maxCachedStatements(2).build())) {
            bounded.register("orders", List.of(
                    new Order(1L, "PAID", 100),
                    new Order(2L, "PAID", 200),
                    new Order(3L, "PENDING", 50)), Order.class);

            bounded.query("SELECT id FROM orders WHERE status = 'PAID'");
            bounded.query("SELECT id FROM orders WHERE amount > 80");
            assertEquals(2, bounded.cachedStatementCount());

            bounded.query("SELECT id FROM orders WHERE amount < 80");
            assertEquals(2, bounded.cachedStatementCount(), "LRU 上限应为 2");

            // 被逐出的最旧模板重新执行仍正确（重新 prepare）
            QueryResult paid = bounded.query("SELECT id FROM orders WHERE status = 'PAID'");
            assertEquals(2, paid.rowCount());
        }
    }

    @Test
    void failedQueryFallsBackAndSessionStillUsable() {
        assertThrows(CalciteMemoryException.class,
                () -> session.query("SELECT * FROM no_such_table WHERE x = 1"));

        QueryResult valid = session.query("SELECT id FROM orders WHERE status = 'PAID'");
        assertEquals(2, valid.rowCount(), "失败查询不应破坏会话");
    }

    @Test
    void restrictedModeStillValidatesParameterizedSql() {
        try (MemoryQueryEngine trusted = MemoryQueryEngine.create();
             MemoryQuerySession restricted = trusted.openSession(
                     SessionConfig.builder().safetyMode(SqlSafetyMode.RESTRICTED).build())) {
            restricted.register("orders", List.of(
                    new Order(1L, "PAID", 100),
                    new Order(2L, "PAID", 200)), Order.class);

            QueryResult paid = restricted.query("SELECT id FROM orders WHERE status = 'PAID'");
            assertEquals(2, paid.rowCount());

            assertThrows(CalciteMemoryException.class,
                    () -> restricted.query("DELETE FROM orders WHERE id = 1"));
        }
    }
}
