package io.github.oatelauser.springplus.calcite.memory.engine;

/**
 * SQL 安全模式。
 * <ul>
 *   <li>{@link #TRUSTED} - 可信后端代码，直接执行完整只读 SQL。</li>
 *   <li>{@link #RESTRICTED} - 配置或有限可信来源的 SQL，先经 {@code safety.SqlAstWhitelist}
 *       解析校验，仅放行只读 SELECT 子集，拒绝 DDL/DML/存储过程/危险函数。</li>
 * </ul>
 */
public enum SqlSafetyMode {
    TRUSTED,
    RESTRICTED
}
