package io.github.oatelauser.springplus.security.annotation;

import org.springframework.core.annotation.AliasFor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.lang.annotation.*;

/**
 * 获取认证对象的主体Principal
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2024-10-15
 * @since 1.0
 */
@Documented
@AuthenticationPrincipal
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface Principal {

    /**
     * 针对认证主体对象的SpEL表达式
     *
     * <pre>
     * public class CustomUserUserDetails extends User {
     *     // ...
     *     public CustomUser getCustomUser() {
     *         return customUser;
     *     }
     * }
     * </pre>
     * <p>
     * 然后用户可以指定如下注释
     *
     * <pre>
     * &#64;AuthenticationPrincipal(expression = "customUser")
     * </pre>
     */
    @AliasFor(annotation = AuthenticationPrincipal.class)
    String expression() default "";

}
