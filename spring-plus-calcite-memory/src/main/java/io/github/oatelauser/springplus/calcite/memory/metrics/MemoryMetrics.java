package io.github.oatelauser.springplus.calcite.memory.metrics;

/**
 * 内存查询指标 SPI：记录查询执行与表注册事件。
 *
 * <p>core 仅依赖此接口（默认 {@link #NOOP} 零开销），Micrometer 等具体实现由 autoconfigure
 * 在类路径存在时注入（{@code @ConditionalOnClass(MeterRegistry.class)}），保持 core 与观测后端解耦。</p>
 *
 * <p>实现应线程安全：{@code recordQuery} / {@code recordTableRegistered} 可能被多个会话并发调用。</p>
 */
public interface MemoryMetrics {

    /** 查询结果分类，用于指标 tag。 */
    enum Outcome {
        /** 成功。 */
        SUCCESS,
        /** 看门狗超时取消。 */
        TIMEOUT,
        /** 解析或执行错误。 */
        ERROR
    }

    /**
     * 记录一次查询执行。
     *
     * @param sql          原始 SQL（实现可按需截断或仅取指纹，避免高基数）
     * @param durationNanos 执行耗时（纳秒）
     * @param rowCount     结果行数（失败时为 0）
     * @param outcome      结果分类
     */
    void recordQuery(String sql, long durationNanos, int rowCount, Outcome outcome);

    /**
     * 记录一次表注册（快照发布）。
     *
     * @param tableName 表名
     * @param rowCount  注册行数
     */
    void recordTableRegistered(String tableName, int rowCount);

    /** 无操作实现，零开销，core 默认使用。 */
    MemoryMetrics NOOP = new MemoryMetrics() {
        @Override
        public void recordQuery(String sql, long durationNanos, int rowCount, Outcome outcome) {
            // no-op：core 默认不采集指标
        }

        @Override
        public void recordTableRegistered(String tableName, int rowCount) {
            // no-op：core 默认不采集指标
        }
    };

    /** 工厂方法，返回 {@link #NOOP}，便于方法引用。 */
    static MemoryMetrics noop() {
        return NOOP;
    }
}
