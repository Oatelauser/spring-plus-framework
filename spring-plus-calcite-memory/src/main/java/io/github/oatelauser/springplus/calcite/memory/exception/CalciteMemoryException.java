package io.github.oatelauser.springplus.calcite.memory.exception;

/**
 * 内存表框架所有异常的基类，统一携带错误码（CM_xxx）。
 */
public abstract class CalciteMemoryException extends RuntimeException {

    private final String errorCode;

    protected CalciteMemoryException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    protected CalciteMemoryException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
