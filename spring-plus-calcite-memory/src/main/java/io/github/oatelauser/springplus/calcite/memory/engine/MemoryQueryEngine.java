package io.github.oatelauser.springplus.calcite.memory.engine;

import io.github.oatelauser.springplus.calcite.memory.convert.TypeConverterRegistry;
import io.github.oatelauser.springplus.calcite.memory.jdbc.CalciteJdbcBootstrap;
import io.github.oatelauser.springplus.calcite.memory.metrics.MemoryMetrics;
import io.github.oatelauser.springplus.calcite.memory.registry.TableRegistry;
import io.github.oatelauser.springplus.calcite.memory.schema.MetadataAdapterRegistry;
import io.github.oatelauser.springplus.calcite.memory.table.MemorySchema;
import org.apache.calcite.jdbc.CalciteConnection;

/**
 * 内存查询引擎：单例式工厂，持有应用级共享 {@link MemorySchema}，产出 {@link MemoryQuerySession}。
 * <p>典型用法：
 * <pre>
 *   try (MemoryQueryEngine engine = MemoryQueryEngine.create();
 *        MemoryQuerySession session = engine.openSession()) {
 *       session.register("orders", orders, Order.class);
 *       QueryResult r = session.query("SELECT id, status FROM orders WHERE status = 'PAID'");
 *   }
 * </pre>
 */
public class MemoryQueryEngine implements AutoCloseable {

    private final TypeConverterRegistry registry;
    private final MetadataAdapterRegistry adapterRegistry;
    private final MemorySchema appSchema;
    private final SessionConfig defaultConfig;
    private final QueryCanceller canceller;
    private final TableBuilder tableBuilder;
    private final MemoryMetrics metrics;

    private MemoryQueryEngine(TypeConverterRegistry registry, MetadataAdapterRegistry adapterRegistry) {
        this(registry, adapterRegistry, SessionConfig.defaults(), MemoryMetrics.NOOP);
    }

    private MemoryQueryEngine(TypeConverterRegistry registry, MetadataAdapterRegistry adapterRegistry,
            SessionConfig defaultConfig, MemoryMetrics metrics) {
        this.registry = registry;
        this.adapterRegistry = adapterRegistry;
        this.appSchema = new MemorySchema();
        this.defaultConfig = defaultConfig;
        this.canceller = new QueryCanceller();
        this.tableBuilder = new TableBuilder(registry, adapterRegistry);
        this.metrics = metrics;
    }

    public static MemoryQueryEngine create() {
        return new MemoryQueryEngine(TypeConverterRegistry.withDefaults(), MetadataAdapterRegistry.withDefaults());
    }

    public static MemoryQueryEngine create(TypeConverterRegistry registry) {
        return new MemoryQueryEngine(registry, MetadataAdapterRegistry.withDefaults());
    }

    public static MemoryQueryEngine create(TypeConverterRegistry registry, MetadataAdapterRegistry adapterRegistry) {
        return new MemoryQueryEngine(registry, adapterRegistry);
    }

    /**
     * 按指定默认会话配置创建引擎，指标用 {@link MemoryMetrics#NOOP}。
     * 供自动配置注入属性化配置。
     */
    public static MemoryQueryEngine create(SessionConfig defaultConfig) {
        return new MemoryQueryEngine(TypeConverterRegistry.withDefaults(), MetadataAdapterRegistry.withDefaults(),
                defaultConfig, MemoryMetrics.NOOP);
    }

    /**
     * 按指定默认会话配置与指标 SPI 创建引擎，供自动配置注入。
     *
     * @param metrics 指标 SPI，null 视为 {@link MemoryMetrics#NOOP}
     */
    public static MemoryQueryEngine create(SessionConfig defaultConfig, MemoryMetrics metrics) {
        return new MemoryQueryEngine(TypeConverterRegistry.withDefaults(), MetadataAdapterRegistry.withDefaults(),
                defaultConfig, metrics == null ? MemoryMetrics.NOOP : metrics);
    }

    /** 应用级共享 Schema，注册其中的表对所有后续会话可见。 */
    public MemorySchema applicationSchema() {
        return appSchema;
    }

    /** 应用级表注册表：在 appSchema 上发布/删除快照，对所有后续会话可见。 */
    public TableRegistry applicationTableRegistry() {
        return new TableRegistry(appSchema, tableBuilder);
    }

    /**
     * 打开新会话：独立 CalciteConnection，会话级 Schema 初始包含应用级表的引用副本。
     */
    public MemoryQuerySession openSession() {
        return openSession(defaultConfig);
    }

    /**
     * 打开带配置的新会话：应用 {@link SessionConfig} 中的安全模式、资源限额与查询超时。
     * RESTRICTED 模式下每条 SQL 执行前经 {@code SqlAstWhitelist} 校验，仅允许只读查询。
     */
    public MemoryQuerySession openSession(SessionConfig config) {
        // 会话级 Schema 以引用方式共享应用级表快照；连接创建统一委托 CalciteJdbcBootstrap
        MemorySchema sessionSchema = new MemorySchema();
        appSchema.copyInto(sessionSchema);
        CalciteConnection conn = CalciteJdbcBootstrap.openConnection(sessionSchema);
        return new MemoryQuerySession(conn, sessionSchema, registry, adapterRegistry, config, canceller, metrics);
    }

    @Override
    public void close() {
        canceller.shutdown();
        appSchema.clear();
    }
}
