package io.github.oatelauser.springplus.calcite.memory;

import io.github.oatelauser.springplus.calcite.memory.annotation.MemoryId;
import io.github.oatelauser.springplus.calcite.memory.annotation.MemoryNested;
import io.github.oatelauser.springplus.calcite.memory.engine.MemoryQueryEngine;
import io.github.oatelauser.springplus.calcite.memory.engine.MemoryQuerySession;
import io.github.oatelauser.springplus.calcite.memory.model.NestedMode;
import io.github.oatelauser.springplus.calcite.memory.result.QueryResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 集合字段拆子表（@MemoryNested(CHILD_TABLE)）与父-子表 JOIN 测试（对应文档 08 测试矩阵 3.5）。
 */
class ChildTableTest {

    static final class Item {
        final String sku;
        final BigDecimal price;
        Item(String sku, BigDecimal price) {
            this.sku = sku;
            this.price = price;
        }
    }

    static final class Order {
        @MemoryId
        final Long id;
        final String orderNo;
        @MemoryNested(mode = NestedMode.CHILD_TABLE, table = "order_item",
            parentKey = "id", foreignKey = "order_id")
        final List<Item> items;
        Order(Long id, String orderNo, List<Item> items) {
            this.id = id;
            this.orderNo = orderNo;
            this.items = items;
        }
    }

    static final class PlainOrder {
        final String no;
        @MemoryNested(mode = NestedMode.CHILD_TABLE, table = "po_item")
        final List<Item> items;
        PlainOrder(String no, List<Item> items) {
            this.no = no;
            this.items = items;
        }
    }

    @Test
    void childTableWithExplicitParentKeyAndJoin() {
        MemoryQueryEngine engine = MemoryQueryEngine.create();
        try (MemoryQuerySession session = engine.openSession()) {
            session.register("orders", List.of(
                new Order(1L, "NO-1", List.of(
                    new Item("A", new BigDecimal("10.00")),
                    new Item("B", new BigDecimal("20.00")))),
                new Order(2L, "NO-2", List.of(
                    new Item("C", new BigDecimal("30.00"))))
            ), Order.class);

            QueryResult parent = session.query("SELECT * FROM orders");
            assertEquals(List.of("id", "order_no"), parent.columnNames());
            assertEquals(2, parent.rowCount());

            QueryResult child = session.query(
                "SELECT sku, price FROM order_item WHERE order_id = 1 ORDER BY price");
            assertEquals(2, child.rowCount());
            assertEquals("A", child.row(0).getString("sku"));
            assertEquals(0, child.row(0).getBigDecimal("price").compareTo(new BigDecimal("10.00")));
            assertEquals("B", child.row(1).getString("sku"));

            QueryResult join = session.query(
                "SELECT o.order_no AS order_no, i.sku AS sku "
                    + "FROM orders o JOIN order_item i ON o.id = i.order_id "
                    + "ORDER BY o.order_no, i.sku");
            assertEquals(3, join.rowCount());
            assertEquals("NO-1", join.row(0).getString("order_no"));
            assertEquals("A", join.row(0).getString("sku"));
            assertEquals("C", join.row(2).getString("sku"));
        }
    }

    @Test
    void childTableWithSyntheticRowIdWhenNoPrimaryKey() {
        MemoryQueryEngine engine = MemoryQueryEngine.create();
        try (MemoryQuerySession session = engine.openSession()) {
            session.register("po", List.of(
                new PlainOrder("X", List.of(new Item("A", new BigDecimal("10.00")))),
                new PlainOrder("Y", List.of(
                    new Item("B", new BigDecimal("20.00")),
                    new Item("C", new BigDecimal("30.00"))))
            ), PlainOrder.class);

            QueryResult parent = session.query("SELECT * FROM po");
            assertTrue(parent.columnNames().contains("__row_id"),
                "父表无主键时应追加合成 __row_id 列: " + parent.columnNames());
            assertEquals(2, parent.rowCount());

            QueryResult child = session.query(
                "SELECT sku, po_row_id FROM po_item ORDER BY po_row_id, sku");
            assertEquals(3, child.rowCount());
            assertEquals("A", child.row(0).getString("sku"));
            assertEquals(0L, child.row(0).getLong("po_row_id"));
            assertEquals("B", child.row(1).getString("sku"));
            assertEquals(1L, child.row(1).getLong("po_row_id"));
            assertEquals("C", child.row(2).getString("sku"));
            assertEquals(1L, child.row(2).getLong("po_row_id"));
        }
    }
}
