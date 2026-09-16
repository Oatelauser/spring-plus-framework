package io.github.oatelauser.springplus.calcite.memory.model;

import lombok.Builder;
import lombok.Value;
import org.apache.calcite.sql.type.SqlTypeName;

/**
 * 内存列的 SQL 类型描述，封装 Calcite {@link SqlTypeName} 与精度/标度/可空性。
 * 数值字段一律使用包装类型，符合 POJO 规范。
 */
@Value
@Builder
public class SqlType {

    SqlTypeName typeName;
    @Builder.Default
    Integer precision = null;
    @Builder.Default
    Integer scale = null;
    @Builder.Default
    boolean nullable = true;

    public static SqlType of(SqlTypeName typeName) {
        return SqlType.builder().typeName(typeName).build();
    }

    public static SqlType of(SqlTypeName typeName, boolean nullable) {
        return SqlType.builder().typeName(typeName).nullable(nullable).build();
    }

    public static SqlType varchar(Integer precision, boolean nullable) {
        return SqlType.builder().typeName(SqlTypeName.VARCHAR).precision(precision).nullable(nullable).build();
    }

    public static SqlType decimal(Integer precision, Integer scale, boolean nullable) {
        return SqlType.builder().typeName(SqlTypeName.DECIMAL).precision(precision).scale(scale).nullable(nullable).build();
    }
}
