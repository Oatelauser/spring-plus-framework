package io.github.oatelauser.springplus.security.annotation;

import java.lang.annotation.*;

/**
 * 跳过认证
 * <p>
 * 必须手动注册{@link io.github.oatelauser.springplus.security.utils.matcher.AnonymousRequestMatcher}请求匹配器才能使用该注解
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2025-06-12
 * @see io.github.oatelauser.springplus.security.utils.matcher.AnonymousRequestMatcher
 * @since 1.0
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.METHOD, ElementType.TYPE })
public @interface RequiresNonLogin {
}
