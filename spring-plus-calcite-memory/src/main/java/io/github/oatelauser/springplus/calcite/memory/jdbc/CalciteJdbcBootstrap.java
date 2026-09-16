package io.github.oatelauser.springplus.calcite.memory.jdbc;

import io.github.oatelauser.springplus.calcite.memory.exception.RegistryException;
import io.github.oatelauser.springplus.calcite.memory.table.MemorySchema;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;
import org.apache.calcite.jdbc.CalciteConnection;

/**
 * JDBC 入口引导：将内存 {@link MemorySchema} 包装为可被外部 JDBC 客户端访问的 {@link CalciteConnection}。
 *
 * <p>本类集中 Calcite 连接创建逻辑（驱动加载、标识符大小写策略、Schema 绑定），
 * 既供 {@code MemoryQueryEngine} 内部会话创建复用，也对外作为直接 JDBC 调用入口
 * （如 5.1 JDBC 集成测试、{@code CalciteDataSource} 构造底层连接）。</p>
 *
 * <p>标识符策略：{@code unquotedCasing=UNCHANGED} —— 注册为 {@code orders} 即可
 * 用 {@code SELECT id FROM orders} 命中，不做大小写折叠，便于和 MyBatis-Flex 标识符约定对齐。</p>
 *
 * <p>线程安全：本类无状态，所有方法可被多线程并发调用；返回的连接由调用方独占使用并负责关闭。</p>
 */
public final class CalciteJdbcBootstrap {

    /** 绑定到根 Schema 的内存 Schema 名，调用方 SQL 中可省略 schema 前缀直接访问表。 */
    public static final String SCHEMA_NAME = "mem";

    private CalciteJdbcBootstrap() {
    }

    /**
     * 打开绑定到指定 Schema 的 {@link CalciteConnection}（无持有器封装）。
     * 调用方负责关闭返回的连接。供需要直接持有连接的场景（如会话内部）使用。
     */
    public static CalciteConnection openConnection(MemorySchema schema) {
        if (schema == null) {
            throw new RegistryException("MemorySchema 不能为 null");
        }
        CalciteConnection connection = createConnection();
        try {
            connection.getRootSchema().add(SCHEMA_NAME, schema);
            connection.setSchema(SCHEMA_NAME);
        } catch (SQLException e) {
            try {
                connection.close();
            } catch (SQLException ce) {
                e.addSuppressed(ce);
            }
            throw new RegistryException("初始化 Calcite Schema 失败", e);
        }
        return connection;
    }

    /**
     * 打开连接并以 {@link CalciteConnectionHolder} 封装，便于 try-with-resources 管理生命周期。
     * 供外部 JDBC 调用方使用。
     */
    public static CalciteConnectionHolder open(MemorySchema schema) {
        return new CalciteConnectionHolder(openConnection(schema));
    }

    private static CalciteConnection createConnection() {
        ensureDriver();
        try {
            Properties props = new Properties();
            props.setProperty("unquotedCasing", "UNCHANGED");
            Connection raw = DriverManager.getConnection("jdbc:calcite:", props);
            return raw.unwrap(CalciteConnection.class);
        } catch (SQLException e) {
            throw new RegistryException("打开 Calcite 连接失败", e);
        }
    }

    private static void ensureDriver() {
        try {
            Class.forName("org.apache.calcite.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new RegistryException("未找到 Calcite JDBC 驱动", e);
        }
    }
}
