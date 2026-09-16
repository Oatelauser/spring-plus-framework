package io.github.oatelauser.springplus.calcite.memory.exception;

/**
 * 资源限制超限：超过 maxRows/maxBytes/最大列数等。
 */
public class ResourceLimitExceededException extends CalciteMemoryException {

    public ResourceLimitExceededException(String message) {
        super("CM_RES", message);
    }

    public ResourceLimitExceededException(String message, Throwable cause) {
        super("CM_RES", message, cause);
    }
}
