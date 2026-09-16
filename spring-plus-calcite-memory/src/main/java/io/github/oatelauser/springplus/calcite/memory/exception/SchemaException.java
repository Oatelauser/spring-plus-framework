package io.github.oatelauser.springplus.calcite.memory.exception;

/**
 * Schema 推断失败：未知类型、空集合无泛型、列名冲突、Map 推断失败等。
 */
public class SchemaException extends CalciteMemoryException {

    public SchemaException(String message) {
        super("CM_SCHE", message);
    }

    public SchemaException(String message, Throwable cause) {
        super("CM_SCHE", message, cause);
    }
}
