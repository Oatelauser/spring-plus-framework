package io.github.oatelauser.springplus.web.validation.clazz;

import io.github.oatelauser.springplus.web.validation.Validatable;
import io.github.oatelauser.springplus.web.validation.ValidationResult;
import jakarta.validation.Constraint;
import jakarta.validation.GroupSequence;
import jakarta.validation.Payload;
import jakarta.validation.groups.Default;

import java.lang.annotation.*;

/**
 * 类级别的校验器注解
 *
 * <p>支持三种方式定义校验逻辑：</p>
 * <ul>
 *   <li>实现 {@link Validatable} 接口</li>
 *   <li>使用默认方法名 {@code validate()}</li>
 *   <li>通过 {@link #method()} 指定自定义方法名</li>
 * </ul>
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2026-01-27
 * @see Validatable
 * @see ValidationResult
 * @see ClassCheckValidator
 * @since 1.0
 */
@Documented
@Target({ ElementType.TYPE }) // Apply to classes
@GroupSequence({ Default.class })
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(ClassValidator.List.class)
@Constraint(validatedBy = ClassCheckValidator.class) // Link to validator
public @interface ClassValidator {

    /**
     * 默认校验异常信息
     */
    String message() default "{io.github.oatelauser.validation.class.failed}";

    /**
     * 校验分组
     */
    Class<?>[] groups() default {};

    /**
     * 负载信息
     */
    Class<? extends Payload>[] payload() default {};

    /**
     * 指定校验方法名（可选，默认使用 {@code validate()} 方法或者实现 {@link Validatable} 接口）
     */
    String method() default "";

    /**
     * 是否快速失败（遇到第一个错误就返回）
     */
    boolean failFast() default false;

    /**
     * 校验执行顺序的策略
     *
     * @see OrderPolicy
     */
    OrderPolicy policy() default OrderPolicy.AFTER_FIELD;

    /**
     * 同级别执行顺序策略的优先级：越低执行顺序越高
     */
    int order() default 0;

    /**
     * 重复注解容器
     */
    @Documented
    @Target({ ElementType.TYPE })
    @Retention(RetentionPolicy.RUNTIME)
    @interface List {
        ClassValidator[] value();
    }

    enum OrderPolicy {

        /**
         * 与字段校验并行执行
         */
        PARALLEL,

        /**
         * 在字段校验之前执行
         */
        BEFORE_FIELD,

        /**
         * 在字段校验之后执行
         */
        AFTER_FIELD
    }

}
