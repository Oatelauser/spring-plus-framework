package io.github.oatelauser.springplus.web.validation.collection;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import org.springframework.util.CollectionUtils;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Collection;
import java.util.Set;

/**
 * 集合元素不可重复
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2026-02-22
 * @see UniqueElementValidator
 * @since 1.0
 */
@Constraint(validatedBy = UniqueElement.UniqueElementValidator.class)
@Target({ ElementType.METHOD, ElementType.FIELD, ElementType.ANNOTATION_TYPE,
        ElementType.CONSTRUCTOR, ElementType.PARAMETER, ElementType.TYPE_USE })
@Retention(RetentionPolicy.RUNTIME)
public @interface UniqueElement {

    String message() default "集合中元素不唯一";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    /**
     * 集合元素不可重复校验器
     *
     * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
     * @date 2026-02-22
     * @since 1.0
     */
    class UniqueElementValidator implements ConstraintValidator<NoNullElement, Collection<?>> {

        @Override
        public boolean isValid(Collection<?> value, ConstraintValidatorContext context) {
            if (CollectionUtils.isEmpty(value)) {
                return true;
            }
            return value.size() == Set.copyOf(value).size();
        }

    }

}
