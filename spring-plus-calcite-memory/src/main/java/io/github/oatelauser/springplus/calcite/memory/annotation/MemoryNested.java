package io.github.oatelauser.springplus.calcite.memory.annotation;

import io.github.oatelauser.springplus.calcite.memory.model.NestedMode;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注嵌套字段的处理方式。
 *
 * <p>单值嵌套对象默认 {@link NestedMode#FLATTEN}，无需显式标注；
 * 集合字段<strong>必须</strong>显式标注 {@link NestedMode#CHILD_TABLE}，否则注册失败。
 *
 * <pre>
 * &#64;MemoryNested(mode = NestedMode.CHILD_TABLE, table = "order_item",
 *               parentKey = "id", foreignKey = "order_id")
 * private List&lt;OrderItem&gt; items;
 * </pre>
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface MemoryNested {

    /**
     * 嵌套模式，默认 FLATTEN。
     */
    NestedMode mode() default NestedMode.FLATTEN;

    /**
     * FLATTEN 模式下的列名前缀；空串表示使用字段名转 snake_case。
     */
    String prefix() default "";

    /**
     * CHILD_TABLE 模式下的子表名；空串表示 父表$字段名。
     */
    String table() default "";

    /**
     * CHILD_TABLE 模式下父表的关联键字段名；空串表示自动选取父表 @MemoryId 列，无则用合成 __row_id。
     */
    String parentKey() default "";

    /**
     * CHILD_TABLE 模式下子表的外键列名；空串表示 父表_关联键。
     */
    String foreignKey() default "";
}
