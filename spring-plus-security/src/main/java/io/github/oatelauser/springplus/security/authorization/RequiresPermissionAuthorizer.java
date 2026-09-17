package io.github.oatelauser.springplus.security.authorization;

import io.github.oatelauser.springplus.security.annotation.RequiresPermission;
import io.github.oatelauser.springplus.security.annotation.RequiresRole;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Set;

import static io.github.oatelauser.springplus.security.authorization.RequiresRoleAuthorizer.location;
import static io.github.oatelauser.springplus.web.utils.AnnotationUtils.findMergedMethodAnnotation;
import static org.springframework.core.annotation.AnnotatedElementUtils.findMergedAnnotation;


/**
 * 用户权限注解认证器
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2024-10-17
 * @since 1.0
 */
@SuppressWarnings("unchecked")
public class RequiresPermissionAuthorizer extends GrantedAuthorityAuthorizer implements ApplicationListener<ApplicationReadyEvent> {

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
        Assert.hasText(source, () -> "方法[" + specificMethod + "]使用@RequiresPermission注解source属性为空");
        if (source.endsWith(":*")) {
            source = source.substring(0, source.length() - 2);
        } else if (source.endsWith(COLON)) {
            source = source.substring(0, source.length() - 1);
        }
        String action = requiresPermission.action();
        Assert.hasText(action, () -> "方法[" + specificMethod + "]使用@RequiresPermission注解action属性为空");
        return Set.of(source + COLON + action);
    }

    @Override
    public int getOrder() {
        return -1;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        ConfigurableApplicationContext applicationContext = event.getApplicationContext();
        for (String beanName : applicationContext.getBeanDefinitionNames()) {
            Class<?> beanType = applicationContext.getType(beanName);
            if (beanType != null) {
                validate(beanType);
            }
        }
    }

    /** 校验单个类上的 {@code @RequiresPermission}（类级 + 方法级），公开供测试直接调用 */
    public void validate(Class<?> beanType) {
        RequiresPermission classLevel = findMergedAnnotation(beanType, RequiresPermission.class);
        checkPermission(beanType, null, classLevel);
        ReflectionUtils.doWithMethods(beanType, method -> checkPermission(beanType,
                method, findMergedMethodAnnotation(method, RequiresPermission.class, beanType)));
    }

    private static void checkPermission(Class<?> beanType, Method method, RequiresPermission requiresPermission) {
        if (requiresPermission == null) {
            return;
        }
        // 与运行期语义对齐：permission 完整表达式（如 "user:delete"）优先，此时候选 source/action
        if (!ObjectUtils.isEmpty(requiresPermission.permission())) {
            return;
        }
        if (!StringUtils.hasText(requiresPermission.source()) || !StringUtils.hasText(requiresPermission.action())) {
            throw new IllegalStateException(location(beanType, method)
                    + "@RequiresPermission 的 source/action 不能为空（启动期失败优于运行期 500）");
        }
    }

}
