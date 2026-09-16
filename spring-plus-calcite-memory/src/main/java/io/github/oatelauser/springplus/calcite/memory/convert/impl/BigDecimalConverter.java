package io.github.oatelauser.springplus.calcite.memory.convert.impl;

import io.github.oatelauser.springplus.calcite.memory.convert.TypeConverter;
import java.math.BigDecimal;
import org.apache.calcite.sql.type.SqlTypeName;

/**
 * BigDecimal 转换器：透传，仅统一声明为 DECIMAL。
 * 金额一律以 BigDecimal 存储，禁止 double 构造；本转换器不做精度变更。
 */
public class BigDecimalConverter implements TypeConverter {

    @Override
    public boolean supports(Class<?> javaType) {
        return javaType == BigDecimal.class;
    }

    @Override
    public SqlTypeName storageSqlType(Class<?> javaType) {
        return SqlTypeName.DECIMAL;
    }

    @Override
    public Object toStorage(Object value) {
        return value;
    }
}
