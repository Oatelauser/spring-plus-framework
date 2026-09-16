package io.github.oatelauser.springplus.calcite.memory.convert.impl;

import io.github.oatelauser.springplus.calcite.memory.convert.TypeConverter;
import org.apache.calcite.sql.type.SqlTypeName;

/**
 * 枚举转换器：存储为枚举常量名（VARCHAR）。
 */
public class EnumConverter implements TypeConverter {

    @Override
    public boolean supports(Class<?> javaType) {
        return javaType != null && Enum.class.isAssignableFrom(javaType);
    }

    @Override
    public SqlTypeName storageSqlType(Class<?> javaType) {
        return SqlTypeName.VARCHAR;
    }

    @Override
    public Object toStorage(Object value) {
        if (value == null) {
            return null;
        }
        return ((Enum<?>) value).name();
    }
}
