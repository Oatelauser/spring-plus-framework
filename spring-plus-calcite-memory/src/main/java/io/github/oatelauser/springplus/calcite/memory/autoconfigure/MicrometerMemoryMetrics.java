package io.github.oatelauser.springplus.calcite.memory.autoconfigure;

import io.github.oatelauser.springplus.calcite.memory.metrics.MemoryMetrics;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * 基于 Micrometer 的 {@link MemoryMetrics} 实现。
 *
 * <p>仅由 {@link CalciteMemoryAutoConfiguration} 在 {@link MeterRegistry} 类路径存在且 bean 可用时装配，
 * 故本类可安全硬依赖 {@code io.micrometer.core.instrument.*}。</p>
 *
 * <p>指标定义：</p>
 * <ul>
 *   <li>{@code calcite.memory.query.duration}（Timer，tag: outcome）— 查询耗时分布</li>
 *   <li>{@code calcite.memory.query.rows}（Counter，tag: outcome）— 查询结果行数累计</li>
 *   <li>{@code calcite.memory.table.registered}（Counter，tag: table）— 表注册行数累计</li>
 * </ul>
 * <p>SQL 原文仅作入参，不作为 tag（避免高基数），实现内忽略。</p>
 */
public final class MicrometerMemoryMetrics implements MemoryMetrics {

    private static final String QUERY_DURATION = "calcite.memory.query.duration";
    private static final String QUERY_ROWS = "calcite.memory.query.rows";
    private static final String TABLE_REGISTERED = "calcite.memory.table.registered";

    private final MeterRegistry registry;

    public MicrometerMemoryMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void recordQuery(String sql, long durationNanos, int rowCount, Outcome outcome) {
        String outcomeTag = outcome.name().toLowerCase(Locale.ROOT);
        Timer.builder(QUERY_DURATION)
                .tag("outcome", outcomeTag)
                .description("Calcite in-memory query duration")
                .register(registry)
                .record(durationNanos, TimeUnit.NANOSECONDS);
        Counter.builder(QUERY_ROWS)
                .tag("outcome", outcomeTag)
                .description("Calcite in-memory query result rows")
                .register(registry)
                .increment(rowCount);
    }

    @Override
    public void recordTableRegistered(String tableName, int rowCount) {
        Counter.builder(TABLE_REGISTERED)
                .tag("table", tableName)
                .description("Calcite in-memory table registered rows")
                .register(registry)
                .increment(rowCount);
    }
}
