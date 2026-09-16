package io.github.oatelauser.springplus.calcite.memory;

import io.github.oatelauser.springplus.calcite.memory.annotation.MemoryColumn;
import io.github.oatelauser.springplus.calcite.memory.annotation.MemoryNested;
import io.github.oatelauser.springplus.calcite.memory.convert.TypeConverterRegistry;
import io.github.oatelauser.springplus.calcite.memory.engine.MemoryQueryEngine;
import io.github.oatelauser.springplus.calcite.memory.engine.MemoryQuerySession;
import io.github.oatelauser.springplus.calcite.memory.exception.NestedMappingException;
import io.github.oatelauser.springplus.calcite.memory.model.NestedMode;
import io.github.oatelauser.springplus.calcite.memory.result.QueryResult;
import io.github.oatelauser.springplus.calcite.memory.result.Row;
import io.github.oatelauser.springplus.calcite.memory.schema.PojoTableAdapter;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * POJO 嵌套扁平化端到端测试（对应文档 08 测试矩阵 3.3 / 3.5）。
 */
class PojoNestedFlattenTest {

    static final class Address {
        final String city;
        final String zip;
        Address(String city, String zip) {
            this.city = city;
            this.zip = zip;
        }
    }

    static final class Buyer {
        final String name;
        final Address address;
        Buyer(String name, Address address) {
            this.name = name;
            this.address = address;
        }
    }

    static final class Order {
        final Long id;
        final String orderNo;
        final BigDecimal amount;
        final Buyer buyer;
        Order(Long id, String orderNo, BigDecimal amount, Buyer buyer) {
            this.id = id;
            this.orderNo = orderNo;
            this.amount = amount;
            this.buyer = buyer;
        }
    }

    @Test
    void flattenNestedObjectAndDeepPath() {
        MemoryQueryEngine engine = MemoryQueryEngine.create();
        try (MemoryQuerySession session = engine.openSession()) {
            session.register("orders", List.of(
                new Order(1L, "NO-1", new BigDecimal("99.50"),
                    new Buyer("Alice", new Address("Beijing", "100000"))),
                new Order(2L, "NO-2", new BigDecimal("10.00"),
                    new Buyer("Bob", new Address("Shanghai", "200000")))
            ), Order.class);

            QueryResult all = session.query("SELECT * FROM orders");
            assertEquals(List.of("id", "order_no", "amount", "buyer_name",
                "buyer_address_city", "buyer_address_zip"), all.columnNames());

            QueryResult r = session.query(
                "SELECT buyer_name, buyer_address_city FROM orders WHERE id = 1");
            assertEquals(1, r.rowCount());
            Row row = r.row(0);
            assertEquals("Alice", row.getString("buyer_name"));
            assertEquals("Beijing", row.getString("buyer_address_city"));
        }
    }

    @Test
    void flattenNullNestedObjectYieldsNullLeafColumns() {
        MemoryQueryEngine engine = MemoryQueryEngine.create();
        try (MemoryQuerySession session = engine.openSession()) {
            session.register("orders", List.of(
                new Order(1L, "NO-1", new BigDecimal("1.00"), new Buyer("Alice", null))
            ), Order.class);
            QueryResult r = session.query(
                "SELECT buyer_name, buyer_address_city FROM orders");
            assertEquals(1, r.rowCount());
            assertEquals("Alice", r.row(0).getString("buyer_name"));
            assertEquals(null, r.row(0).getString("buyer_address_city"));
        }
    }

    @Test
    void collectionFieldWithoutDeclarationFails() {
        TypeConverterRegistry registry = TypeConverterRegistry.withDefaults();
        NestedMappingException ex = assertThrows(NestedMappingException.class, () ->
            new PojoTableAdapter<>("t", WithRawCollection.class, registry));
        assertTrue(ex.getMessage().contains("必须显式 @MemoryNested"));
    }

    @Test
    void cycleDetectionFails() {
        TypeConverterRegistry registry = TypeConverterRegistry.withDefaults();
        assertThrows(NestedMappingException.class, () ->
            new PojoTableAdapter<>("t", CycleA.class, registry));
    }

    @Test
    void columnNameConflictFails() {
        TypeConverterRegistry registry = TypeConverterRegistry.withDefaults();
        NestedMappingException ex = assertThrows(NestedMappingException.class, () ->
            new PojoTableAdapter<>("t", ConflictHolder.class, registry));
        assertTrue(ex.getMessage().contains("列名冲突"));
    }

    // ---- 错误用例 fixture ----

    static final class WithRawCollection {
        Long id;
        List<String> tags;
    }

    static final class CycleA {
        CycleB b;
    }

    static final class CycleB {
        CycleA a;
    }

    static final class ConflictInner {
        String name;
    }

    static final class ConflictHolder {
        @MemoryColumn("inner_name")
        String override;
        @MemoryNested
        ConflictInner inner;
    }
}
