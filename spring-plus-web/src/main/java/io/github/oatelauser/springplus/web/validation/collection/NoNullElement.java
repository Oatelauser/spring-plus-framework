package io.github.oatelauser.springplus.web.validation.collection;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.*;
import java.util.Collection;

/**
 * 判断集合元素是否有为空的
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2024-11-27
 * @since 1.0
 */
@Documented
@Constraint(validatedBy = NoNullElement.NoNullElementValidator.class)
@Target({ ElementType.METHOD, ElementType.FIELD, ElementType.ANNOTATION_TYPE,
        ElementType.CONSTRUCTOR, ElementType.PARAMETER, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
public @interface NoNullElement {

    String message() default "集合中不应包含null元素";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    /**
     * 判断集合元素是否有为空的
     *
     * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
     * @date 2024-11-27
     * @since 1.0
     */
    public class NoNullElementValidator implements ConstraintValidator<NoNullElement, Collection<?>> {

        @Override
        public boolean isValid(Collection value, ConstraintValidatorContext context) {
            if (value != null) {
                for (Object elem : value) {
                    if (elem == null) {
                        return false;
                    }
                }
            }
            return true;
        }

    }

}
