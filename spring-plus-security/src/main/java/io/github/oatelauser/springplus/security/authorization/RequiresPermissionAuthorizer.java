package io.github.oatelauser.springplus.security.authorization;

import io.github.oatelauser.springplus.security.annotation.RequiresPermission;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Set;

import static io.github.oatelauser.springplus.web.utils.AnnotationUtils.findMergedMethodAnnotation;


/**
 * 用户权限注解认证器
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2024-10-17
 * @since 1.0
 */
@Component
@SuppressWarnings("unchecked")
public class RequiresPermissionAuthorizer extends GrantedAuthorityAuthorizer {

    /**
     * 权限键拼接分隔符（内联自 CacheUtils.COLON，避免 security 对 boot 模块的传递依赖）
     */
    private static final String COLON = ":";

    @Override
    public Class<? extends Annotation>[] getAnnotationClass() {
        return new Class[]{ RequiresPermission.class };
    }

    @Override
    public Annotation processAuthorizedMethod(Method specificMethod, Class<?> targetClass) {
        return findMergedMethodAnnotation(specificMethod, RequiresPermission.class, targetClass);
    }

    @Override
    protected Set<String> processAuthorizedAnnotation(Annotation annotation, Method specificMethod) {
        RequiresPermission requiresPermission = (RequiresPermission) annotation;
        if (requiresPermission == null) {
            return Set.of();
        }
        String[] permissions = requiresPermission.permission();
        if (!ObjectUtils.isEmpty(permissions)) {
            return Set.of(permissions);
        }

        String source = requiresPermission.source();
        Assert.hasText(source, () -> "方法[" + specificMethod +
                "]使用@RequiresPermission注解source属性为空");
        if (source.endsWith(":*")) {
            source = source.substring(0, source.length() - 2);
        } else if (source.endsWith(COLON)) {
            source = source.substring(0, source.length() - 1);
        }
        String action = requiresPermission.action();
        Assert.hasText(action, () -> "方法[" + specificMethod +
                "]使用@RequiresPermission注解action属性为空");
        return Set.of(source + COLON + action);
    }

    @Override
    public int getOrder() {
        return -1;
    }

}
