package io.github.oatelauser.springplus.calcite.memory.engine;

import io.github.oatelauser.springplus.calcite.memory.safety.ResourceLimits;

/**
 * 会话配置：时区、资源限制、SQL 安全模式。不可变，通过 {@link Builder} 构建。
 *
 * <p>数值项经 {@link ResourceLimits} 聚合，由 {@link #limits()} 暴露给会话强制执行。
 * 默认值：时区 UTC、单表 10 万行、64 表、查询结果 10 万行、超时 5 秒、
 * 语句模板缓存 128 条、{@link SqlSafetyMode#TRUSTED}。
 */
public final class SessionConfig {

    private final String timeZone;
    private final SqlSafetyMode safetyMode;
    private final ResourceLimits limits;
    private final int maxCachedStatements;

    private SessionConfig(Builder b) {
        this.timeZone = b.timeZone;
        this.safetyMode = b.safetyMode;
        this.maxCachedStatements = b.maxCachedStatements;
        this.limits = ResourceLimits.builder()
            .maxRowsPerTable(b.maxRowsPerTable)
            .maxTables(b.maxTables)
            .maxResultRows(b.maxResultRows)
            .queryTimeoutMillis(b.queryTimeoutMillis)
            .build();
    }

    public String timeZone() {
        return timeZone;
    }

    public SqlSafetyMode safetyMode() {
        return safetyMode;
    }

    public ResourceLimits limits() {
        return limits;
    }

    public long maxRowsPerTable() {
        return limits.getMaxRowsPerTable();
    }

    public int maxTables() {
        return limits.getMaxTables();
    }

    public long maxResultRows() {
        return limits.getMaxResultRows();
    }

    public long queryTimeoutMillis() {
        return limits.getQueryTimeoutMillis();
    }

    /** 语句模板缓存上限（LRU），0 由构建器校验拒绝。 */
    public int maxCachedStatements() {
        return maxCachedStatements;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static SessionConfig defaults() {
        return new Builder().build();
    }

    public static final class Builder {
        private String timeZone = "UTC";
        private long maxRowsPerTable = 100_000L;
        private int maxTables = 64;
        private long maxResultRows = 100_000L;
        private long queryTimeoutMillis = 5_000L;
        private int maxCachedStatements = 128;
        private SqlSafetyMode safetyMode = SqlSafetyMode.TRUSTED;

        public Builder timeZone(String timeZone) {
            this.timeZone = timeZone;
            return this;
        }

        public Builder maxRowsPerTable(long maxRowsPerTable) {
            this.maxRowsPerTable = maxRowsPerTable;
            return this;
        }

        public Builder maxTables(int maxTables) {
            this.maxTables = maxTables;
            return this;
        }

        public Builder maxResultRows(long maxResultRows) {
            this.maxResultRows = maxResultRows;
            return this;
        }

        public Builder queryTimeoutMillis(long queryTimeoutMillis) {
            this.queryTimeoutMillis = queryTimeoutMillis;
            return this;
        }

        public Builder maxCachedStatements(int maxCachedStatements) {
            this.maxCachedStatements = maxCachedStatements;
            return this;
        }

        public Builder safetyMode(SqlSafetyMode safetyMode) {
            this.safetyMode = safetyMode;
            return this;
        }

        public SessionConfig build() {
            if (timeZone == null || timeZone.isBlank()) {
                throw new IllegalArgumentException("timeZone 不能为空");
            }
            if (safetyMode == null) {
                throw new IllegalArgumentException("safetyMode 不能为 null");
            }
            if (maxCachedStatements < 1) {
                throw new IllegalArgumentException("maxCachedStatements 必须 >= 1");
            }
            return new SessionConfig(this);
        }
    }
}
