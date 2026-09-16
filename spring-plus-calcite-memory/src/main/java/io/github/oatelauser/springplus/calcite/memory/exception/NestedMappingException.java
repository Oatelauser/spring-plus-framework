package io.github.oatelauser.springplus.calcite.memory.exception;

/**
 * 嵌套映射失败：循环引用、集合未显式声明嵌套模式、列名冲突等。
 * （P2 引入，P1 预置异常类型。）
 */
public class NestedMappingException extends CalciteMemoryException {

    public NestedMappingException(String message) {
        super("CM_NEST", message);
    }

    public NestedMappingException(String message, Throwable cause) {
        super("CM_NEST", message, cause);
    }
}
