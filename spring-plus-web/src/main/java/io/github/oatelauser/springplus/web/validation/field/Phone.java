package io.github.oatelauser.springplus.web.validation.field;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * 手机号码
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PhoneValidator.class)
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.ANNOTATION_TYPE,
	ElementType.CONSTRUCTOR, ElementType.PARAMETER, ElementType.TYPE_USE})
public @interface Phone {

	String message() default "手机号格式错误";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

}
