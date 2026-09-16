package io.github.oatelauser.springplus.calcite.memory.schema;

import io.github.oatelauser.springplus.calcite.memory.convert.TypeConverter;
import io.github.oatelauser.springplus.calcite.memory.convert.TypeConverterRegistry;
import io.github.oatelauser.springplus.calcite.memory.exception.SchemaException;
import org.apache.calcite.sql.type.SqlTypeName;

import java.util.Date;
import java.util.UUID;

/**
 * 标量 Java 类型 -&gt; SQL 类型的映射工具，供 POJO / Map / 标量列表适配器共用，避免重复实现。
 */
final class ScalarTypeMapping {

    private ScalarTypeMapping() {
    }

    /**
     * 结合注册表推断 Java 类的 SQL 类型名：优先使用已注册 {@link TypeConverter}，
     * 否则回落到内置基本类型映射。
     */
    static SqlTypeName of(Class<?> t, TypeConverterRegistry registry, String fieldName) {
        TypeConverter conv = registry.resolve(t);
        if (conv != null) {
            return conv.storageSqlType(t);
        }
        return mapJavaToSql(t, fieldName);
    }

    /**
     * 判断类型是否可作为单列标量值（无需嵌套展开）。不依赖注册表，
     * 用于适配器工厂的元素类型分发。
     */
    static boolean isScalarValueType(Class<?> t) {
        if (t.isPrimitive() || t == String.class || t == char.class || t == Character.class) {
            return true;
        }
        if (Number.class.isAssignableFrom(t) || t == Boolean.class) {
            return true;
        }
        if (t == byte[].class || t == UUID.class) {
            return true;
        }
        if (t.isEnum() || Enum.class.isAssignableFrom(t)) {
            return true;
        }
        if (java.time.temporal.TemporalAccessor.class.isAssignableFrom(t)) {
            return true;
        }
        if (Date.class.isAssignableFrom(t)) {
            return true;
        }
        return false;
    }

    static SqlTypeName mapJavaToSql(Class<?> t, String fieldName) {
        if (t == String.class || t == char.class || t == Character.class) {
            return SqlTypeName.VARCHAR;
        }
        if (t == boolean.class || t == Boolean.class) {
            return SqlTypeName.BOOLEAN;
        }
        if (t == byte.class || t == Byte.class || t == short.class || t == Short.class
                || t == int.class || t == Integer.class) {
            return SqlTypeName.INTEGER;
        }
        if (t == long.class || t == Long.class) {
            return SqlTypeName.BIGINT;
        }
        if (t == float.class || t == Float.class || t == double.class || t == Double.class) {
            return SqlTypeName.DOUBLE;
        }
        if (t == byte[].class) {
            return SqlTypeName.VARBINARY;
        }
        if (t == java.math.BigInteger.class) {
            return SqlTypeName.DECIMAL;
        }
        throw new SchemaException("字段 " + fieldName + " 类型 " + t.getName()
                + " 不支持；请注册 TypeConverter 或标注 @MemoryColumn(ignore=true)");
    }
}
