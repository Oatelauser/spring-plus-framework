package io.github.oatelauser.springplus.web.validation.field;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Set;

/**
 * 指定合法值列表校验器
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2026-02-22
 * @since 1.0
 */
public class ListValuesValidator implements ConstraintValidator<ListValues, Object> {

    private Set<String> allowedValues;

    @Override
    public void initialize(ListValues annotation) {
        this.allowedValues = Set.of(annotation.value());
    }

    @Override
    public boolean isValid(Object value, ConstraintValidatorContext context) {
        if (value == null) return true;
        return allowedValues.contains(value.toString());
    }

}
