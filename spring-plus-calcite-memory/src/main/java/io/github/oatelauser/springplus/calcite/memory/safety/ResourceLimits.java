package io.github.oatelauser.springplus.calcite.memory.safety;

import io.github.oatelauser.springplus.calcite.memory.exception.ResourceLimitExceededException;
import lombok.Builder;
import lombok.Value;

/**
 * 资源限制：每表行数上限、表数量上限、查询结果行数上限、查询超时毫秒。
 *
 * <p>由 {@link io.github.oatelauser.springplus.calcite.memory.engine.SessionConfig} 聚合，会话在注册表与查询时调用
 * {@link #checkRegisterTable}/{@link #checkQueryResult} 强制约束，超限抛
 * {@link ResourceLimitExceededException}。
 *
 * <p>数值字段使用基本类型：限制值在 Builder 中带默认值，永不为 null，语义为"配置值"而非 RPC 可空字段。
 */
@Value
@Builder
public class ResourceLimits {

    /** 单张注册表的最大行数。 */
    @Builder.Default
    long maxRowsPerTable = 100_000L;

    /** 单会话最大表数量。 */
    @Builder.Default
    int maxTables = 64;

    /** 单次查询返回的最大行数。 */
    @Builder.Default
    long maxResultRows = 100_000L;

    /** 查询超时毫秒，超时由 QueryCanceller 取消。 */
    @Builder.Default
    long queryTimeoutMillis = 5_000L;

    public void checkRegisterTable(int currentTableCount, int tablesToAdd, long rowsToRegister, String tableName) {
        if (currentTableCount + tablesToAdd > maxTables) {
            throw new ResourceLimitExceededException("表数量将达上限 " + maxTables
                + "（当前 " + currentTableCount + "，新增 " + tablesToAdd + "，拒绝注册 " + tableName + "）");
        }
        if (rowsToRegister > maxRowsPerTable) {
            throw new ResourceLimitExceededException("表 " + tableName + " 行数 " + rowsToRegister
                + " 超过单表上限 " + maxRowsPerTable);
        }
    }

    /**
     * 查询结果行数校验。
     */
    public void checkQueryResult(long rowCount) {
        if (rowCount > maxResultRows) {
            throw new ResourceLimitExceededException("查询结果行数 " + rowCount + " 超过上限 " + maxResultRows);
        }
    }
}
