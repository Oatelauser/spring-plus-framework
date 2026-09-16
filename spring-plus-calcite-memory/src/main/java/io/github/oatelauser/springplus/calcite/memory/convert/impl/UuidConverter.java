package io.github.oatelauser.springplus.calcite.memory.convert.impl;

import io.github.oatelauser.springplus.calcite.memory.convert.TypeConverter;
import java.util.UUID;
import org.apache.calcite.sql.type.SqlTypeName;

/**
 * UUID 转换器：存储为字符串（VARCHAR）。
 */
public class UuidConverter implements TypeConverter {

    @Override
    public boolean supports(Class<?> javaType) {
        return javaType == UUID.class;
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
        return value.toString();
    }
}
