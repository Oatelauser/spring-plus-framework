package io.github.oatelauser.springplus.security.annotation;

import java.lang.annotation.*;

import static io.github.oatelauser.springplus.security.annotation.RequiresRole.ROLE_SUPER_ADMIN;


/**
 * 权限校验系统角色
 *
 * @author <a href="mailto:545896770@qq.com">DearYang</a>
 * @date 2023-05-08
 * @since 1.0
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@RequiresRole(role = ROLE_SUPER_ADMIN)
@Target({ ElementType.METHOD, ElementType.TYPE })
public @interface RequiresAdminRole {
}
