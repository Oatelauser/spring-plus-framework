package io.github.oatelauser.springplus.web.validation.field;

import jakarta.validation.Payload;

/**
 * 枚举值
 *
 * <pre>
 *  @EnumValue(enumClass = OrderStatus.class, method = "getCode", message = "订单状态不合法")
 *  private Integer status;
 * </pre>
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2026-02-22
 * @since 1.0
 */
public @interface EnumValue {

    String message() default "枚举值不在允许的范围内";

    /**
     * 指定枚举类
     */
    Class<? extends Enum<?>> enumClass();

    /**
     * 枚举中用于匹配的方法名（默认 name()）
     * 比如自定义枚举的 getCode() / getValue()
     */
    String method() default "name";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

}
