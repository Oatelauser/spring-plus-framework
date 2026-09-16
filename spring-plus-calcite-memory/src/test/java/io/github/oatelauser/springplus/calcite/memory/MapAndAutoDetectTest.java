package io.github.oatelauser.springplus.calcite.memory;

import io.github.oatelauser.springplus.calcite.memory.engine.MemoryQueryEngine;
import io.github.oatelauser.springplus.calcite.memory.engine.MemoryQuerySession;
import io.github.oatelauser.springplus.calcite.memory.exception.RegistryException;
import io.github.oatelauser.springplus.calcite.memory.exception.SchemaException;
import io.github.oatelauser.springplus.calcite.memory.schema.MetadataAdapterRegistry;
import io.github.oatelauser.springplus.calcite.memory.result.QueryResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Map 推断、标量列表、自动检测与适配器优先级链测试（对应文档 08 测试矩阵 3.6）。
 */
class MapAndAutoDetectTest {

    @Test
    void mapListAutoDetectAndQuery() {
        MemoryQueryEngine engine = MemoryQueryEngine.create();
        try (MemoryQuerySession session = engine.openSession()) {
            session.register("accounts", List.of(
                map("id", 1, "name", "Alice", "balance", new BigDecimal("99.50")),
                map("id", 2L, "name", "Bob", "balance", new BigDecimal("10.00"))
            ));
            QueryResult all = session.query("SELECT * FROM accounts");
            assertEquals(List.of("id", "name", "balance"), all.columnNames());
            assertEquals(2, all.rowCount());

            QueryResult r = session.query("SELECT name, balance FROM accounts WHERE id = 1");
            assertEquals(1, r.rowCount());
            assertEquals("Alice", r.row(0).getString("name"));
            assertEquals(0, r.row(0).getBigDecimal("balance").compareTo(new BigDecimal("99.50")));
        }
    }

    @Test
    void mapTypePromotionIntegerToLong() {
        MemoryQueryEngine engine = MemoryQueryEngine.create();
        try (MemoryQuerySession session = engine.openSession()) {
            session.register("nums", List.of(
                map("id", 1),
                map("id", 2L),
                map("id", 3)
            ));
            QueryResult r = session.query("SELECT id FROM nums ORDER BY id");
            assertEquals(3, r.rowCount());
            assertEquals(1L, r.row(0).getLong("id"));
            assertEquals(2L, r.row(1).getLong("id"));
            assertEquals(3L, r.row(2).getLong("id"));
        }
    }

    @Test
    void mapCrossFamilyTypeRejected() {
        MemoryQueryEngine engine = MemoryQueryEngine.create();
        try (MemoryQuerySession session = engine.openSession()) {
            SchemaException ex = assertThrows(SchemaException.class, () ->
                session.register("bad", List.of(
                    map("v", 1),
                    map("v", "x")
                )));
            assertTrue(ex.getMessage().contains("类型不兼容"));
        }
    }

    @Test
    void scalarListAutoDetectSingleValueColumn() {
        MemoryQueryEngine engine = MemoryQueryEngine.create();
        try (MemoryQuerySession session = engine.openSession()) {
            session.register("tags", List.of("alpha", "beta", "gamma"));
            QueryResult r = session.query("SELECT \"value\" FROM tags WHERE \"value\" = 'beta'");
            assertEquals(1, r.rowCount());
            assertEquals("beta", r.row(0).getString("value"));
        }
    }

    @Test
    void emptyListAutoDetectRejected() {
        MemoryQueryEngine engine = MemoryQueryEngine.create();
        try (MemoryQuerySession session = engine.openSession()) {
            RegistryException ex = assertThrows(RegistryException.class, () ->
                session.register("empty", List.of()));
            assertTrue(ex.getMessage().contains("空 List"));
        }
    }

    @Test
    void nullElementRejected() {
        MemoryQueryEngine engine = MemoryQueryEngine.create();
        try (MemoryQuerySession session = engine.openSession()) {
            assertThrows(RegistryException.class, () ->
                session.register("n", Arrays.asList("a", null)));
        }
    }

    @Test
    void mixedTypeListRejected() {
        MemoryQueryEngine engine = MemoryQueryEngine.create();
        try (MemoryQuerySession session = engine.openSession()) {
            RegistryException ex = assertThrows(RegistryException.class, () ->
                session.register("m", List.of("a", 1)));
            assertTrue(ex.getMessage().contains("元素类型不一致"));
        }
    }

    @Test
    void samePriorityAdapterConflictDetected() {
        MetadataAdapterRegistry r = new MetadataAdapterRegistry();
        r.register(10, new MetadataAdapterRegistry.PojoAdapterFactory());
        r.register(10, new MetadataAdapterRegistry.PojoAdapterFactory());
        RegistryException ex = assertThrows(RegistryException.class, () ->
            r.resolve("t", List.of(), Marker.class, io.github.oatelauser.springplus.calcite.memory.convert.TypeConverterRegistry.withDefaults()));
        assertTrue(ex.getMessage().contains("冲突"));
    }

    static final class Marker {
        Long id;
    }

    private static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }
}
