package io.github.oatelauser.springplus.calcite.memory.mybatis;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link CalciteDialectAdapter} 单元测试：分页子句拼接与标识符引用。
 */
class CalciteDialectAdapterTest {

    private final CalciteDialectAdapter adapter = new CalciteDialectAdapter();

    @Test
    void paginateOffsetZeroOmitsOffset() {
        assertEquals("SELECT id FROM orders LIMIT 10", adapter.paginate("SELECT id FROM orders", 0, 10));
    }

    @Test
    void paginatePositiveOffsetAppendsOffset() {
        assertEquals("SELECT id FROM orders LIMIT 5 OFFSET 20", adapter.paginate("SELECT id FROM orders", 20, 5));
    }

    @Test
    void paginateStripsTrailingSemicolon() {
        assertEquals("SELECT id FROM orders LIMIT 10", adapter.paginate("SELECT id FROM orders;", 0, 10));
    }

    @Test
    void paginateRejectsNonPositiveLimit() {
        assertThrows(IllegalArgumentException.class, () -> adapter.paginate("SELECT 1", 0, 0));
        assertThrows(IllegalArgumentException.class, () -> adapter.paginate("SELECT 1", 0, -1));
    }

    @Test
    void quoteIdentifierWrapsAndEscapes() {
        assertEquals("\"orders\"", adapter.quoteIdentifier("orders"));
        assertEquals("\"a\"\"b\"", adapter.quoteIdentifier("a\"b"));
    }
}
