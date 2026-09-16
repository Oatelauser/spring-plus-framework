package io.github.oatelauser.springplus.calcite.memory.exception;

/**
 * 查询超时：超过 Session 配置的查询时限，Statement/ResultSet 应已关闭。
 */
public class QueryTimeoutException extends CalciteMemoryException {

    public QueryTimeoutException(String message) {
        super("CM_TIME", message);
    }

    public QueryTimeoutException(String message, Throwable cause) {
        super("CM_TIME", message, cause);
    }
}
