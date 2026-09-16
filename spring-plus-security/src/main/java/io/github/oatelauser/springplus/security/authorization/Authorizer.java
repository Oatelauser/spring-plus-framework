package io.github.oatelauser.springplus.security.authorization;

import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.util.CollectionUtils;

import java.util.Collection;
import java.util.List;

import static io.github.oatelauser.springplus.security.annotation.RequiresRole.ROLE_SUPER_ADMIN;
import static io.github.oatelauser.springplus.security.authorization.AnnotationAuthorizationDecision.ALLOW;


/**
 * 自定义授权逻辑
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2024-10-17
 * @see io.github.oatelauser.springplus.security.annotation.Authorize
 * @since 1.0
 */
public interface Authorizer {

    /**
     * 判断方法权限
     *
     * @param authentication 认证对象
     * @param mi             拦截请求方法
     * @return true-授权 false-权限不足
     */
    AuthorizationResult verify(Authentication authentication, AnnotationMethodInvocation mi);

    /**
     * 判断方法权限
     *
     * @param authentication 认证对象
     * @param mi             拦截请求方法
     * @return true-授权 false-权限不足
     */
    @SuppressWarnings("unchecked")
    default AuthorizationResult check(Authentication authentication, AnnotationMethodInvocation mi) {
        Collection<GrantedAuthority> grantedAuthorities =
                (Collection<GrantedAuthority>) authentication.getAuthorities();
        return determineAdminRole(grantedAuthorities) ? ALLOW : this.verify(authentication, mi);
    }

    /**
     * 判断是否有系统管理员角色
     *
     * @param grantedAuthorities 权限集合
     * @return true-系统管理员
     */
    static boolean determineAdminRole(Collection<GrantedAuthority> grantedAuthorities) {
        if (CollectionUtils.isEmpty(grantedAuthorities)) {
            return false;
        }
        // 不限定集合类型与顺序：Spring Security 常规 User 的 authorities 是 Set，
        // 按元素匹配而非 instanceof List + 首元素（顺序敏感的权限判断不可靠）
        return grantedAuthorities.stream()
                .anyMatch(authority -> ROLE_SUPER_ADMIN.equals(authority.getAuthority()));
    }

}
