package io.github.oatelauser.springplus.web.validation.field;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

/**
 * 枚举值校验器
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2026-02-22
 * @since 1.0
 */
public class EnumValueValidator implements ConstraintValidator<EnumValue, Object> {

    /**
     * 原始类型值集合 —— 处理类型完全匹配的情况
     */
    private Set<Object> allowedValues;

    /**
     * 字符串值集合 —— 处理前端传 String 但枚举值是 Integer 等跨类型场景
     */
    private Set<String> allowedStringValues;

    @Override
    public void initialize(EnumValue annotation) {
        Class<? extends Enum<?>> enumClass = annotation.enumClass();
        String methodName = annotation.method();
        Enum<?>[] constants = enumClass.getEnumConstants();

        this.allowedValues = new HashSet<>(constants.length * 2);
        this.allowedStringValues = new HashSet<>(constants.length * 2);

        try {
            Method method = enumClass.getMethod(methodName);
            for (Enum<?> constant : constants) {
                Object val = method.invoke(constant);
                if (val != null) {
                    allowedValues.add(val);
                    allowedStringValues.add(val.toString()); // 预处理，避免 isValid 重复转换
                }
            }
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(String.format(
                    "枚举 %s 不存在 public 方法 %s()", enumClass.getSimpleName(), methodName), e);
        } catch (Exception e) {
            throw new IllegalStateException(String.format(
                    "枚举 %s 调用 %s() 失败", enumClass.getSimpleName(), methodName), e);
        }
    }

    @Override
    public boolean isValid(Object value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        // O(1) 查找，无反射，无 toString 开销（除非类型不匹配才降级）
        return allowedValues.contains(value)
                || allowedStringValues.contains(value.toString());
    }

}
