package io.github.oatelauser.springplus.security.annotation;


import io.github.oatelauser.springplus.security.authorization.Authorizer;

import java.lang.annotation.*;

import static io.github.oatelauser.springplus.security.annotation.Relation.AND;


/**
 * 自定义授权注解
 * <p>
 * 通过指定的bean对象{@link Authorizer}自定义授权逻辑
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2024-10-17
 * @see Authorizer
 * @since 1.0
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.METHOD, ElementType.TYPE })
public @interface Authorize {

    /**
     * 实现{@link Authorizer}的bean名称
     */
    String beanName() default "";

    /**
     * 实现{@link Authorizer}的类型
     */
    Class<? extends Authorizer>[] beanClass() default {};

    /**
     * 存在多个{@link Authorizer}的类型时，如何判断
     */
    Relation relation() default AND;

}
