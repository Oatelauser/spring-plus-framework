package io.github.oatelauser.springplus.calcite.memory.convert;

import org.apache.calcite.sql.type.SqlTypeName;

/**
 * 类型转换器扩展点：将 Calcite 无法原生存储的 Java 类型转换为存储类型，并给出对应的 {@link SqlTypeName}。
 * <p>注册到 {@link TypeConverterRegistry} 后，Schema 推断与行转换会自动调用。
 */
public interface TypeConverter {

    /** 是否支持该 Java 类型。 */
    boolean supports(Class<?> javaType);

    /** 该 Java 类型转换后对应的列 SQL 类型。 */
    SqlTypeName storageSqlType(Class<?> javaType);

    /** 将字段值转换为存储值；入参为 null 时返回 null。 */
    Object toStorage(Object value);
}
