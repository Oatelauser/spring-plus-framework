package io.github.oatelauser.springplus.calcite.memory.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注字段为内存列，可覆盖列名或忽略该字段。
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface MemoryColumn {

    /**
     * 列名；空串表示按字段名转 snake_case。
     */
    String value() default "";

    /**
     * 是否忽略该字段（不出现在表中）。
     */
    boolean ignore() default false;
}
