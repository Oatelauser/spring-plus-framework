package io.github.oatelauser.springplus.calcite.memory.engine;

import io.github.oatelauser.springplus.calcite.memory.convert.TypeConverterRegistry;
import io.github.oatelauser.springplus.calcite.memory.exception.ExecutionException;
import io.github.oatelauser.springplus.calcite.memory.metrics.MemoryMetrics;
import io.github.oatelauser.springplus.calcite.memory.exception.QueryTimeoutException;
import io.github.oatelauser.springplus.calcite.memory.exception.RegistryException;
import io.github.oatelauser.springplus.calcite.memory.exception.SqlException;
import io.github.oatelauser.springplus.calcite.memory.result.QueryResult;
import io.github.oatelauser.springplus.calcite.memory.result.Row;
import io.github.oatelauser.springplus.calcite.memory.schema.MetadataAdapter;
import io.github.oatelauser.springplus.calcite.memory.schema.MetadataAdapterRegistry;
import io.github.oatelauser.springplus.calcite.memory.registry.TableRegistry;
import io.github.oatelauser.springplus.calcite.memory.safety.SqlAstWhitelist;
import io.github.oatelauser.springplus.calcite.memory.table.MemorySchema;
import io.github.oatelauser.springplus.calcite.memory.table.MemoryTable;

import java.math.BigDecimal;
import java.sql.ParameterMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLSyntaxErrorException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.calcite.jdbc.CalciteConnection;
import org.apache.calcite.sql.parser.SqlParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 查询会话：持有独立 {@link CalciteConnection} 与会话级 {@link MemorySchema}。
 * 注册表后即可执行 SQL；会话级表仅在本会话可见，关闭后随连接释放。
 * 应用级表在 {@link MemoryQueryEngine#openSession()} 时以引用方式共享（表本身为不可变快照）。
 */
public class MemoryQuerySession implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(MemoryQuerySession.class);
    private static final String SCHEMA_NAME = "mem";

    private final CalciteConnection connection;
    private final MemorySchema schema;
    private final SessionConfig config;
    private final QueryCanceller canceller;
    private final TableBuilder tableBuilder;
    private final MemoryMetrics metrics;
    private final StatementCache statementCache;

    MemoryQuerySession(CalciteConnection connection, MemorySchema schema, TypeConverterRegistry registry,
            MetadataAdapterRegistry adapterRegistry, SessionConfig config, QueryCanceller canceller,
            MemoryMetrics metrics) {
        this.connection = connection;
        this.schema = schema;
        this.config = config;
        this.canceller = canceller;
        this.metrics = metrics == null ? MemoryMetrics.NOOP : metrics;
        this.tableBuilder = new TableBuilder(registry, adapterRegistry);
        this.statementCache = new StatementCache(config.maxCachedStatements());
        try {
            connection.getRootSchema().add(SCHEMA_NAME, schema);
            connection.setSchema(SCHEMA_NAME);
        } catch (SQLException e) {
            throw new RegistryException("初始化会话 Schema 失败", e);
        }
    }

    public <T> void register(String tableName, List<T> items, Class<T> elementType) {
        if (tableName == null || tableName.isBlank()) {
            throw new RegistryException("表名不能为空");
        }
        if (items == null) {
            throw new RegistryException("数据不能为 null: " + tableName);
        }
        if (elementType == null) {
            throw new RegistryException("元素类型不能为 null: " + tableName);
        }
        MetadataAdapter<Object> adapter = tableBuilder.resolve(tableName, items, elementType);
        registerAdapter(tableName, adapter, items);
    }

    /**
     * 注册重载：自动推断元素类型并选择适配器。
     * <ul>
     *   <li>{@code List<Map<String,Object>>} -&gt; Map 表</li>
     *   <li>{@code List<String>} / {@code List<Integer>} 等标量列表 -&gt; 单列 value 表</li>
     *   <li>POJO 列表 -&gt; POJO 表</li>
     * </ul>
     * 空 List 无法推断类型，请改用 {@link #register(String, List, Class)} 显式指定。
     * 元素类型不一致或存在 null 元素均抛 {@link RegistryException}。
     */
    public void register(String tableName, List<?> items) {
        if (tableName == null || tableName.isBlank()) {
            throw new RegistryException("表名不能为空");
        }
        Class<?> elementType = TableBuilder.inferElementType(items, tableName);
        MetadataAdapter<Object> adapter = tableBuilder.resolve(tableName, items, elementType);
        registerAdapter(tableName, adapter, items);
    }

    private void registerAdapter(String tableName, MetadataAdapter<Object> adapter, List<?> items) {
        int tablesToAdd = tableBuilder.tablesToAdd(adapter);
        config.limits().checkRegisterTable(schema.tableCount(), tablesToAdd, items.size(), tableName);
        List<TableBuilder.BuiltTable> built = tableBuilder.build(tableName, adapter, items);
        for (TableBuilder.BuiltTable t : built) {
            schema.register(t.name(), new MemoryTable(t.schema(), t.store()));
        }
        metrics.recordTableRegistered(tableName, items.size());
    }

    /**
     * 删除会话级表；不存在返回 false。子表需调用方分别删除（会话级表互不联动）。
     */
    public boolean dropTable(String tableName) {
        if (tableName == null || tableName.isBlank()) {
            return false;
        }
        boolean existed = schema.get(tableName) != null;
        schema.unregister(tableName);
        return existed;
    }

    /** 会话级表注册表：在本会话私有 schema 上发布/删除快照，仅本会话可见。 */
    public TableRegistry sessionTableRegistry() {
        return new TableRegistry(schema, tableBuilder);
    }

    /**
     * 执行 SQL 查询，返回 {@link QueryResult}。
     * 默认 schema 为 mem，可直接 {@code SELECT * FROM orders}。
     *
     * <p>内部透明参数化（Druid 风格）：SQL 中 WHERE/HAVING 的字面量被
     * {@link SqlParameterizer} 归并为 {@code ?} 模板，模板做 {@link StatementCache}
     * 的 key 复用 PreparedStatement，提取的字面量绑参执行——结构相同、值不同的
     * 查询共享同一条语句，跳过 Calcite 重复的解析/优化/代码生成。
     * 模板路径失败时自动回退原始 SQL，行为与未引入缓存时一致；
     * 超时取消不回退（重跑慢查询只会白等两倍时长）。</p>
     */
    public QueryResult query(String sql) {
        if (sql == null || sql.isBlank()) {
            throw new SqlException("SQL 不能为空");
        }
        if (config.safetyMode() == SqlSafetyMode.RESTRICTED) {
            SqlAstWhitelist.validate(sql);
        }
        SqlParameterizer.ParameterizedSql parameterized = SqlParameterizer.parameterize(sql);
        long timeoutMillis = config.queryTimeoutMillis();
        long start = System.nanoTime();
        MemoryMetrics.Outcome outcome = MemoryMetrics.Outcome.SUCCESS;
        int rowCount = 0;
        try {
            QueryResult result;
            if (parameterized.parameters().isEmpty()) {
                result = executeCached(parameterized.templateSql(), List.of());
            } else {
                try {
                    result = executeCached(parameterized.templateSql(), parameterized.parameters());
                } catch (SQLException | RuntimeException e) {
                    if (isTimeoutCancellation(e)) {
                        throw e;
                    }
                    // 模板路径失败（绑参类型不匹配/反解析不兼容）：回退原始 SQL，行为与未引入缓存时一致
                    log.debug("参数化模板执行失败，回退原始 SQL: {}", e.getMessage(), e);
                    result = executeCached(sql, List.of());
                }
            }
            rowCount = result.rowCount();
            return result;
        } catch (SQLException e) {
            if (e instanceof SQLSyntaxErrorException || hasParseError(e)) {
                outcome = MemoryMetrics.Outcome.ERROR;
                throw new SqlException("SQL 解析失败: " + e.getMessage(), e);
            }
            if (isTimeoutCancellation(e)) {
                outcome = MemoryMetrics.Outcome.TIMEOUT;
                throw new QueryTimeoutException("查询超时（" + timeoutMillis + "ms）: " + e.getMessage(), e);
            }
            outcome = MemoryMetrics.Outcome.ERROR;
            throw new ExecutionException("查询执行失败: " + e.getMessage(), e);
        } finally {
            metrics.recordQuery(sql, System.nanoTime() - start, rowCount, outcome);
        }
    }

    /** 会话是否已关闭：底层连接已关闭即视为会话已关闭。 */
    public boolean isClosed() {
        try {
            return connection.isClosed();
        } catch (SQLException e) {
            return true;
        }
    }

    /**
     * 判定异常是否源于看门狗触发的 {@link java.sql.Statement#cancel()}。
     * Calcite 在取消时通常抛出包裹 InterruptedException 的 SQLException，或消息含 "cancel"。
     */
    private static boolean isTimeoutCancellation(Throwable e) {
        Throwable cur = e;
        for (int i = 0; i < 8 && cur != null; i++) {
            if (cur instanceof InterruptedException) {
                return true;
            }
            String message = cur.getMessage();
            if (message != null && message.toLowerCase().contains("cancel")) {
                return true;
            }
            cur = cur.getCause();
        }
        return false;
    }

    private static boolean hasParseError(Throwable e) {
        Throwable cur = e;
        for (int i = 0; i < 8 && cur != null; i++) {
            if (cur instanceof SqlParseException) {
                return true;
            }
            cur = cur.getCause();
        }
        return false;
    }

    /**
     * 经语句缓存执行：命中复用，未命中 prepare 后入缓存；绑参并执行。
     * 任何 {@link SQLException}（含超时取消）先把该语句逐出缓存（可能处于坏状态）再抛出。
     */
    private QueryResult executeCached(String cacheKey, List<Object> parameters) throws SQLException {
        PreparedStatement ps = statementCache.acquire(connection, cacheKey, schema.version());
        try {
            QueryCanceller.Handle handle = canceller.scheduleCancel(ps, config.queryTimeoutMillis());
            try (ResultSet rs = executeWithParameters(ps, parameters)) {
                QueryResult result = mapResult(rs);
                config.limits().checkQueryResult(result.rowCount());
                return result;
            } finally {
                handle.close();
            }
        } catch (SQLException e) {
            statementCache.evict(cacheKey);
            throw e;
        }
    }

    /**
     * 绑参并执行：参数个数与模板占位符数校验，不匹配在执行前明确失败；
     * 值按 Calcite 推断的参数 JDBC 类型精确转换后绑定（避免 Integer 绑到 BIGINT 参数
     * 触发生成代码强转 ClassCastException）。
     */
    private static ResultSet executeWithParameters(PreparedStatement ps, List<Object> parameters)
            throws SQLException {
        if (parameters.isEmpty()) {
            return ps.executeQuery();
        }
        ps.clearParameters();
        ParameterMetaData metaData = ps.getParameterMetaData();
        int expected = metaData.getParameterCount();
        if (expected != parameters.size()) {
            throw new SqlException("参数个数不匹配: 模板需要 " + expected
                    + " 个占位符，实际提供 " + parameters.size() + " 个参数");
        }
        for (int i = 0; i < parameters.size(); i++) {
            ps.setObject(i + 1, convertToParamType(metaData.getParameterType(i + 1), parameters.get(i)));
        }
        return ps.executeQuery();
    }

    /** 数值按目标参数类型转换（BIGINT->Long、INTEGER->Integer、DECIMAL->BigDecimal、浮点->Double）；其余原样。 */
    private static Object convertToParamType(int jdbcType, Object value) {
        if (!(value instanceof Number n)) {
            return value;
        }
        return switch (jdbcType) {
            case Types.BIGINT -> Long.valueOf(n.longValue());
            case Types.INTEGER, Types.SMALLINT, Types.TINYINT -> Integer.valueOf(n.intValue());
            case Types.DECIMAL, Types.NUMERIC -> toBigDecimal(n);
            case Types.DOUBLE, Types.FLOAT, Types.REAL -> Double.valueOf(n.doubleValue());
            default -> value;
        };
    }

    private static BigDecimal toBigDecimal(Number n) {
        if (n instanceof BigDecimal bd) {
            return bd;
        }
        if (n instanceof Double || n instanceof Float) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        return BigDecimal.valueOf(n.longValue());
    }

    /** 当前语句模板缓存条目数，供测试与观测。 */
    int cachedStatementCount() {
        return statementCache.size();
    }

    private QueryResult mapResult(ResultSet rs) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        int n = meta.getColumnCount();
        List<String> columnNames = new ArrayList<>(n);
        Map<String, Integer> indexByName = new HashMap<>();
        for (int i = 1; i <= n; i++) {
            String name = meta.getColumnLabel(i);
            columnNames.add(name);
            indexByName.put(name, i - 1);
        }
        List<Row> rows = new ArrayList<>();
        while (rs.next()) {
            Object[] values = new Object[n];
            for (int i = 1; i <= n; i++) {
                values[i - 1] = rs.getObject(i);
            }
            rows.add(new Row(values, indexByName));
        }
        return new QueryResult(columnNames, rows);
    }

    @Override
    public void close() {
        statementCache.close();
        try {
            connection.close();
        } catch (SQLException e) {
            log.warn("关闭 CalciteConnection 失败: {}", e.getMessage(), e);
        }
    }

}
