package io.github.oatelauser.springplus.calcite.memory.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注一个 POJO 类作为内存表，可指定表名。
 * <pre>
 *   &#64;MemoryTable("orders")
 *   public class Order { ... }
 * </pre>
 * 不指定表名时由注册时的名称决定。
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface MemoryTable {

    /**
     * 表名；空串表示注册时显式指定。
     */
    String value() default "";
}
