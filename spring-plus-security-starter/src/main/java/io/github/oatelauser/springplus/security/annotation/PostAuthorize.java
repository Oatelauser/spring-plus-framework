package io.github.oatelauser.springplus.security.annotation;


import io.github.oatelauser.springplus.security.authorization.Authorizer;

import java.lang.annotation.*;

import static io.github.oatelauser.springplus.security.annotation.Relation.AND;


/**
 * 自定义后置授权注解
 * <p>
 * 通过指定的bean对象{@link Authorizer}自定义后置授权逻辑
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2024-10-22
 * @since 1.0
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.METHOD, ElementType.TYPE })
public @interface PostAuthorize {

    /**
     * 实现{@link Authorizer}的bean名称
     */
    String beanName() default "";

    /**
     * 实现{@link Authorizer}的类型
     * <p>
     * 指定多个则取并集，有任何一个成功则成功，所有失败则失败
     */
    Class<? extends Authorizer>[] beanClass() default {};

    /**
     * 存在多个{@link Authorizer}的类型时，如何判断
     */
    Relation relation() default AND;

}
