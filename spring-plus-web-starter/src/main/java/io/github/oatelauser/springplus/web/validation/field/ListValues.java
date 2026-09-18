package io.github.oatelauser.springplus.web.validation.field;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * 指定合法值列表
 *
 * <pre>
 * @ListValues(value = {"MALE", "FEMALE", "UNKNOWN"}, message = "性别值不合法")
 * private String gender;
 * </pre>
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2026-02-22
 * @since 1.0
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ListValuesValidator.class)
@Target({ ElementType.METHOD, ElementType.FIELD, ElementType.ANNOTATION_TYPE,
        ElementType.CONSTRUCTOR, ElementType.PARAMETER, ElementType.TYPE_USE })
public @interface ListValues {

    /**
     * 字符串形式的合法值列表
     */
    String[] value();

    String message() default "值不在允许的范围内";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

}
