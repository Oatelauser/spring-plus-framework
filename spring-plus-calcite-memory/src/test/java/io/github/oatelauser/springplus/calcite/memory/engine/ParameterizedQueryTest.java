package io.github.oatelauser.springplus.calcite.memory.engine;

import io.github.oatelauser.springplus.calcite.memory.exception.CalciteMemoryException;
import io.github.oatelauser.springplus.calcite.memory.result.QueryResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 显式绑参查询 API（V03 / CWE-89）：{@code query(sql, params)} 的正确性与注入面闭合。
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2026-09-16
 * @since 1.0.1
 */
class ParameterizedQueryTest {

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
        session.register("orders", List.of(
                new Order(1L, "PAID", 100),
                new Order(2L, "PAID", 200),
                new Order(3L, "PENDING", 50)), Order.class);
    }

    @AfterEach
    void tearDown() {
        session.close();
        engine.close();
    }

    @Test
    void boundQueryMatchesLiteralQuery() {
        QueryResult bound = session.query("SELECT id FROM orders WHERE status = ?", "PAID");
        QueryResult literal = session.query("SELECT id FROM orders WHERE status = 'PAID'");
        assertEquals(literal.rowCount(), bound.rowCount());
        assertEquals(2, bound.rowCount());

        QueryResult none = session.query("SELECT id FROM orders WHERE status = ?", "NOT_EXIST");
        assertEquals(0, none.rowCount());
    }

    @Test
    void injectionPayloadIsTreatedAsData() {
        // 经典注入串作为参数值绑定：只是普通字符串，不改变 SQL 结构
        QueryResult result = session.query("SELECT id FROM orders WHERE status = ?",
                "PAID' OR '1'='1");
        assertEquals(0, result.rowCount(), "注入串必须查不到任何行（作为数据比较）");
    }

    @Test
    void multipleParametersBindInOrder() {
        QueryResult result = session.query(
                "SELECT id FROM orders WHERE status = ? AND amount > ?", "PAID", 100);
        assertEquals(1, result.rowCount());
        assertEquals(2L, result.row(0).getLong("id"));
    }

    @Test
    void parameterCountMismatchFailsExplicitly() {
        assertThrows(CalciteMemoryException.class,
                () -> session.query("SELECT id FROM orders WHERE status = ? AND amount > ?", "PAID"));
    }

    @Test
    void repeatedSameTemplateSharesCachedStatement() {
        session.query("SELECT id FROM orders WHERE status = ?", "PAID");
        session.query("SELECT id FROM orders WHERE status = ?", "PENDING");
        assertTrue(session.cachedStatementCount() >= 1, "同型模板应命中语句缓存");
    }

}
