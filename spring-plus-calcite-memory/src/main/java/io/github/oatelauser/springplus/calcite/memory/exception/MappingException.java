package io.github.oatelauser.springplus.calcite.memory.exception;

/**
 * 结果映射失败：Record 构造参数缺失、类型不兼容、多余列（严格模式）等。
 */
public class MappingException extends CalciteMemoryException {

    public MappingException(String message) {
        super("CM_MAP", message);
    }

    public MappingException(String message, Throwable cause) {
        super("CM_MAP", message, cause);
    }
}
