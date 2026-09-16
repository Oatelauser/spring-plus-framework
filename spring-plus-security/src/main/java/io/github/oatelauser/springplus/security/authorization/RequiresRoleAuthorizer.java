package io.github.oatelauser.springplus.security.authorization;

import io.github.oatelauser.springplus.security.annotation.RequiresAdminRole;
import io.github.oatelauser.springplus.security.annotation.RequiresRole;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.config.core.GrantedAuthorityDefaults;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static io.github.oatelauser.springplus.web.utils.AnnotationUtils.findMergedMethodAnnotation;


/**
 * 用户角色注解认证器
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2024-10-17
 * @see RequiresRole
 * @see RequiresAdminRole
 * @since 1.0
 */
@SuppressWarnings("unchecked")
public class RequiresRoleAuthorizer extends GrantedAuthorityAuthorizer {

    private final GrantedAuthorityDefaults authorityDefaults;

    public RequiresRoleAuthorizer(ObjectProvider<GrantedAuthorityDefaults> authorityDefaults) {
        this.authorityDefaults = authorityDefaults.getIfAvailable(() -> new GrantedAuthorityDefaults("ROLE_"));
    }

    @Override
    public Class<? extends Annotation>[] getAnnotationClass() {
        return new Class[]{ RequiresAdminRole.class, RequiresRole.class };
    }

    @Override
    public Annotation processAuthorizedMethod(Method specificMethod, Class<?> targetClass) {
        return findMergedMethodAnnotation(specificMethod, RequiresRole.class, targetClass);
    }

    @Override
    protected Set<String> processAuthorizedAnnotation(Annotation annotation, Method specificMethod) {
        RequiresRole requiresRole = (RequiresRole) annotation;
        String[] roles;
        if (requiresRole == null || ObjectUtils.isEmpty(roles = (requiresRole.role()))) {
            return Set.of();
        }
        return Arrays.stream(roles).map(this::resolveRolePermission).collect(Collectors.toSet());
    }

    /**
     * 给权限标识设置默认值
     *
     * @param anyPerm 权限标识
     * @return 真正的权限
     */
    private String resolveRolePermission(String anyPerm) {
        String rolePrefix;
        if (authorityDefaults == null || !StringUtils.hasText((rolePrefix = authorityDefaults.getRolePrefix()))) {
            return anyPerm;
        }
        if (!anyPerm.startsWith(rolePrefix)) {
            anyPerm = rolePrefix + anyPerm;
        }
        return anyPerm;
    }

    @Override
    public int getOrder() {
        return 10;
    }

}
