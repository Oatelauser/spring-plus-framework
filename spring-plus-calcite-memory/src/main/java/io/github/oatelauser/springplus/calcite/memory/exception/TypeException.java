package io.github.oatelauser.springplus.calcite.memory.exception;

/**
 * 类型转换失败：声明 NOT NULL 但值为 null、类型不兼容、无可用转换器等。
 */
public class TypeException extends CalciteMemoryException {

    public TypeException(String message) {
        super("CM_TYPE", message);
    }

    public TypeException(String message, Throwable cause) {
        super("CM_TYPE", message, cause);
    }
}
