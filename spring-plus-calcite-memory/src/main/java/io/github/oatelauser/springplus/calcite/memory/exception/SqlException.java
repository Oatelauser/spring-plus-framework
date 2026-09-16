package io.github.oatelauser.springplus.calcite.memory.exception;

/**
 * SQL 解析或校验失败：语法错误、RESTRICTED 模式命中黑名单、不支持特性等。
 */
public class SqlException extends CalciteMemoryException {

    public SqlException(String message) {
        super("CM_SQL", message);
    }

    public SqlException(String message, Throwable cause) {
        super("CM_SQL", message, cause);
    }
}
