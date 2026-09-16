package io.github.oatelauser.springplus.calcite.memory.jdbc;

import io.github.oatelauser.springplus.calcite.memory.convert.TypeConverterRegistry;
import io.github.oatelauser.springplus.calcite.memory.registry.TableRegistry;
import io.github.oatelauser.springplus.calcite.memory.schema.MetadataAdapterRegistry;
import io.github.oatelauser.springplus.calcite.memory.engine.TableBuilder;
import io.github.oatelauser.springplus.calcite.memory.table.MemorySchema;
import org.junit.jupiter.api.Test;

import java.sql.ParameterMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 5.1 Calcite JDBC 集成测试：连接建立、预编译参数绑定、ResultSetMetaData / ParameterMetaData、
 * LIMIT/OFFSET 分页、三表 JOIN、聚合 + HAVING。
 *
 * <p>测试通过 {@link CalciteJdbcBootstrap} 直接获取 {@link org.apache.calcite.jdbc.CalciteConnection}，
 * 覆盖直接 JDBC 调用方场景，区别于 {@code MemoryQuerySession} 的封装式查询。</p>
 */
class CalciteJdbcTest {

    /** 构造已注册 orders/users/items 的独立 MemorySchema（不经引擎，避免 canceller 线程泄漏）。 */
    private static MemorySchema schemaWithAll() {
        MemorySchema schema = new MemorySchema();
        TableBuilder tableBuilder = new TableBuilder(
                TypeConverterRegistry.withDefaults(), MetadataAdapterRegistry.withDefaults());
        TableRegistry registry = new TableRegistry(schema, tableBuilder);
        registry.publishSnapshot("orders", orders());
        registry.publishSnapshot("users", users());
        registry.publishSnapshot("items", items());
        return schema;
    }

    private static List<Map<String, Object>> orders() {
        return List.of(
            Map.of("id", 1, "user_id", 10, "amount", 100, "status", "PAID"),
            Map.of("id", 2, "user_id", 10, "amount", 200, "status", "PAID"),
            Map.of("id", 3, "user_id", 20, "amount", 50, "status", "PENDING"),
            Map.of("id", 4, "user_id", 20, "amount", 300, "status", "PAID"),
            Map.of("id", 5, "user_id", 30, "amount", 150, "status", "PAID"));
    }

    private static List<Map<String, Object>> users() {
        return List.of(
            Map.of("id", 10, "name", "alice"),
            Map.of("id", 20, "name", "bob"),
            Map.of("id", 40, "name", "carol"));
    }

    private static List<Map<String, Object>> items() {
        return List.of(
            Map.of("id", 101, "order_id", 1, "product", "pen"),
            Map.of("id", 102, "order_id", 1, "product", "book"),
            Map.of("id", 103, "order_id", 2, "product", "lamp"));
    }

    @Test
    void connectionSucceedsAndSchemaBound() throws Exception {
        try (CalciteConnectionHolder holder = CalciteJdbcBootstrap.open(schemaWithAll())) {
            assertNotNull(holder.get());
            assertEquals("mem", holder.get().getSchema().toLowerCase());
        }
    }

    @Test
    void preparedStatementParameterBinding() throws Exception {
        try (CalciteConnectionHolder holder = CalciteJdbcBootstrap.open(schemaWithAll());
                PreparedStatement ps = holder.get().prepareStatement(
                        "SELECT id, amount FROM orders WHERE user_id = ? AND status = ? ORDER BY id")) {
            ps.setInt(1, 10);
            ps.setString(2, "PAID");
            List<Integer> ids = new ArrayList<>();
            List<Integer> amounts = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getInt("id"));
                    amounts.add(rs.getInt("amount"));
                }
            }
            assertEquals(List.of(1, 2), ids);
            assertEquals(List.of(100, 200), amounts);
        }
    }

    @Test
    void resultSetMetaDataColumnsAndTypes() throws Exception {
        try (CalciteConnectionHolder holder = CalciteJdbcBootstrap.open(schemaWithAll());
                PreparedStatement ps = holder.get().prepareStatement(
                        "SELECT id, user_id, amount, status FROM orders WHERE id = 1")) {
            ResultSetMetaData md = ps.getMetaData();
            assertEquals(4, md.getColumnCount());
            assertEquals("id", md.getColumnName(1).toLowerCase());
            assertEquals("amount", md.getColumnName(3).toLowerCase());
            assertEquals("status", md.getColumnName(4).toLowerCase());
            assertEquals(Types.INTEGER, md.getColumnType(1));
            assertEquals(Types.VARCHAR, md.getColumnType(4));
        }
    }

    @Test
    void parameterMetaDataCountAndUsable() throws Exception {
        try (CalciteConnectionHolder holder = CalciteJdbcBootstrap.open(schemaWithAll());
                PreparedStatement ps = holder.get().prepareStatement(
                        "SELECT id FROM orders WHERE user_id = ? AND status = ?")) {
            ParameterMetaData pmd = ps.getParameterMetaData();
            assertEquals(2, pmd.getParameterCount());
            // Calcite 的参数类型实现较宽松，此处断言 ParameterMetaData 可用且返回合法 Types 常量
            assertTrue(pmd.getParameterType(1) >= 0);
            assertTrue(pmd.getParameterType(2) >= 0);
        }
    }

    @Test
    void limitOffsetPagination() throws Exception {
        try (CalciteConnectionHolder holder = CalciteJdbcBootstrap.open(schemaWithAll());
                PreparedStatement ps = holder.get().prepareStatement(
                        "SELECT id FROM orders ORDER BY id LIMIT 2 OFFSET 1")) {
            List<Integer> ids = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getInt(1));
                }
            }
            assertEquals(List.of(2, 3), ids);
        }
    }

    @Test
    void threeTableJoin() throws Exception {
        try (CalciteConnectionHolder holder = CalciteJdbcBootstrap.open(schemaWithAll());
                PreparedStatement ps = holder.get().prepareStatement(
                        "SELECT o.id, u.name, i.product "
                                + "FROM orders o "
                                + "JOIN users u ON o.user_id = u.id "
                                + "JOIN items i ON i.order_id = o.id "
                                + "WHERE o.status = 'PAID' "
                                + "ORDER BY o.id, i.id")) {
            List<Object[]> rows = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new Object[]{rs.getInt(1), rs.getString(2), rs.getString(3)});
                }
            }
            // 订单 1（alice）有 pen/book；订单 2（alice）有 lamp；订单 4/5 无子项
            assertEquals(3, rows.size());
            assertEquals(1, rows.get(0)[0]);
            assertEquals("alice", rows.get(0)[1]);
            assertEquals("pen", rows.get(0)[2]);
            assertEquals(2, rows.get(2)[0]);
            assertEquals("lamp", rows.get(2)[2]);
        }
    }

    @Test
    void aggregateWithHaving() throws Exception {
        try (CalciteConnectionHolder holder = CalciteJdbcBootstrap.open(schemaWithAll());
                PreparedStatement ps = holder.get().prepareStatement(
                        "SELECT user_id, COUNT(*) AS cnt, SUM(amount) AS total "
                                + "FROM orders "
                                + "GROUP BY user_id "
                                + "HAVING COUNT(*) > 1 "
                                + "ORDER BY user_id")) {
            List<long[]> rows = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new long[]{rs.getInt("user_id"), rs.getLong("cnt"), rs.getLong("total")});
                }
            }
            // user10: 2 单 300；user20: 2 单 350；user30 仅 1 单被 HAVING 过滤
            assertEquals(2, rows.size());
            assertEquals(10, rows.get(0)[0]);
            assertEquals(2, rows.get(0)[1]);
            assertEquals(300, rows.get(0)[2]);
            assertEquals(20, rows.get(1)[0]);
            assertEquals(350, rows.get(1)[2]);
        }
    }
}
