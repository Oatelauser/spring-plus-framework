package io.github.oatelauser.springplus.security.annotation;

import java.lang.annotation.*;

/**
 * 权限校验用户角色
 *
 * @author <a href="mailto:545896770@qq.com">DearYang</a>
 * @date 2023-05-08
 * @since 1.0
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.METHOD, ElementType.TYPE })
public @interface RequiresRole {

    /**
     * 系统超级管理员角色
     */
    String ROLE_SUPER_ADMIN = "ROLE_SUPER_ADMIN";
    String ADMIN_ROLE_NAME = "超级管理员";

    /**
     * 管理员
     */
    String ROLE_ADMIN = "ROLE_ADMIN";

    /**
     * 普通用户
     */
    String ROLE_USER = "ROLE_USER";

    /**
     * 需要的角色，默认不需要角色
     *
     * @return 角色
     */
    String[] role();

}
