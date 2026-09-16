package io.github.oatelauser.springplus.calcite.memory.exception;

/**
 * 表注册失败：表名重复、非法表名、超过规模上限等。
 */
public class RegistryException extends CalciteMemoryException {

    public RegistryException(String message) {
        super("CM_REG", message);
    }

    public RegistryException(String message, Throwable cause) {
        super("CM_REG", message, cause);
    }
}
