package io.github.oatelauser.springplus.security.authorization;

import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.util.CollectionUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Set;

import static io.github.oatelauser.springplus.security.authorization.AnnotationAuthorizationDecision.ALLOW;

/**
 * 抽象权限处理的认证器
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2024-10-17
 * @since 1.0
 */
@SuppressWarnings("unchecked")
public abstract class GrantedAuthorityAuthorizer implements AnnotationAuthorizer {

    @Override
    public AuthorizationResult verify(Authentication authentication, AnnotationMethodInvocation mi) {
        Collection<GrantedAuthority> grantedAuthorities =
                (Collection<GrantedAuthority>) authentication.getAuthorities();
        return this.verify(mi, grantedAuthorities);
    }

    private AuthorizationResult verify(AnnotationMethodInvocation mi, Collection<GrantedAuthority> grantedAuthorities) {
        Annotation annotation = mi.getAnnotation();
        Method specificMethod = mi.getSpecificMethod();
        Set<String> authority = this.processAuthorizedAnnotation(annotation, specificMethod);
        // fail-closed（CWE-862）：注解存在但解析为空权限 = 配置错误（如 @RequiresRole(role = {})），
        // 必须拒绝而非放行；annotation 为 null 表示本授权器不处理该调用，走原语义
        if (annotation != null && authority.isEmpty()) {
            return AnnotationAuthorizationDecision.deny(Set.<String>of());
        }
        // 开始校验权限
        return this.verify(authority, grantedAuthorities);
    }

    /**
     * 校验方法上的权限信息和角色的权限信息
     *
     * @param requireAuthorities 方法上的权限信息
     * @param grantedAuthorities 角色的权限信息
     * @return true-权限通过 false-权限不足
     */
    protected AuthorizationResult verify(Set<String> requireAuthorities, Collection<GrantedAuthority> grantedAuthorities) {
        if (CollectionUtils.isEmpty(requireAuthorities)) {
            return ALLOW;
        }
        for (GrantedAuthority grantedAuthority : grantedAuthorities) {
            if (requireAuthorities.contains(grantedAuthority.getAuthority())) {
                return ALLOW;
            }
        }
        return AnnotationAuthorizationDecision.deny(requireAuthorities);
    }

    /**
     * 处理授权注解提取权限信息
     *
     * @param annotation     授权注解
     * @param specificMethod 授权方法
     * @return 权限信息
     */
    protected abstract Set<String> processAuthorizedAnnotation(Annotation annotation, Method specificMethod);

}
