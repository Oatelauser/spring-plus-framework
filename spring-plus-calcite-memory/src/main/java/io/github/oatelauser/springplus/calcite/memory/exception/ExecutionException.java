package io.github.oatelauser.springplus.calcite.memory.exception;

/**
 * 查询执行失败：Calcite 内部错误、结果映射失败等。
 */
public class ExecutionException extends CalciteMemoryException {

    public ExecutionException(String message) {
        super("CM_EXEC", message);
    }

    public ExecutionException(String message, Throwable cause) {
        super("CM_EXEC", message, cause);
    }
}
