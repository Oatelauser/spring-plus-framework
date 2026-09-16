package io.github.oatelauser.springplus.calcite.memory.autoconfigure;

import io.github.oatelauser.springplus.calcite.memory.engine.SessionConfig;
import io.github.oatelauser.springplus.calcite.memory.engine.SqlSafetyMode;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 内存表查询框架配置项，前缀 {@code calcite.memory}。
 *
 * <p>所有数值项使用包装类型，显式给定默认值；{@link #toSessionConfig()} 对未设置（{@code null}）的项
 * 委托 {@link SessionConfig.Builder} 自身默认值兜底，避免基本类型「0 掩盖未配置」的语义混淆。</p>
 */
@Data
@ConfigurationProperties("calcite.memory")
public class CalciteMemoryProperties {

    /** 是否启用自动装配，默认 true。 */
    private Boolean enabled = Boolean.TRUE;

    /** SQL 安全模式，默认 TRUSTED；RESTRICTED 经 AST 白名单只放行只读 SELECT 子集。 */
    private SqlSafetyMode safetyMode = SqlSafetyMode.TRUSTED;

    /** 会话时区，默认 UTC。 */
    private String timeZone = "UTC";

    /** 单引擎最大注册表数，默认 64。 */
    private Integer maxTables = 64;

    /** 单表最大行数，默认 100000。 */
    private Long maxRowsPerTable = 100_000L;

    /** 单次查询最大结果行数，默认 100000。 */
    private Long maxResultRows = 100_000L;

    /** 单次查询超时（毫秒），默认 5000。 */
    private Long queryTimeoutMillis = 5_000L;

    /** 语句模板缓存上限（LRU 条目数），默认 128。 */
    private Integer maxCachedStatements = 128;

    /**
     * 将配置映射为不可变 {@link SessionConfig}。未显式设置的项沿用 {@link SessionConfig.Builder} 默认值，
     * 保证部分配置缺失时仍可构建合法会话配置。
     */
    public SessionConfig toSessionConfig() {
        SessionConfig.Builder builder = SessionConfig.builder();
        if (safetyMode != null) {
            builder.safetyMode(safetyMode);
        }
        if (timeZone != null && !timeZone.isBlank()) {
            builder.timeZone(timeZone);
        }
        if (maxTables != null) {
            builder.maxTables(maxTables);
        }
        if (maxRowsPerTable != null) {
            builder.maxRowsPerTable(maxRowsPerTable);
        }
        if (maxResultRows != null) {
            builder.maxResultRows(maxResultRows);
        }
        if (queryTimeoutMillis != null) {
            builder.queryTimeoutMillis(queryTimeoutMillis);
        }
        if (maxCachedStatements != null) {
            builder.maxCachedStatements(maxCachedStatements);
        }
        return builder.build();
    }
}
