package io.github.oatelauser.springplus.calcite.memory.model;

import lombok.Builder;
import lombok.Value;

/**
 * 单列规格：列名、SQL 类型、取值来源路径（POJO 字段名）、是否业务主键。
 * 不可变值对象。
 */
@Value
@Builder
public class ColumnSpec {

    /**
     * 列名，按 snake_case 规范化后的最终列名。
     */
    String name;

    /**
     * 列的 SQL 类型。
     */
    SqlType sqlType;

    /**
     * 取值来源路径：POJO 场景为字段名，Map 场景为 key。
     */
    String sourcePath;

    /**
     * 是否业务主键（{@link io.github.oatelauser.springplus.calcite.memory.annotation.MemoryId} 标注）。
     */
    @Builder.Default
    boolean primaryKey = false;
}
