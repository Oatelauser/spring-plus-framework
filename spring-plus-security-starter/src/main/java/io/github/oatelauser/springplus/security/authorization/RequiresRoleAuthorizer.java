package io.github.oatelauser.springplus.security.authorization;

import io.github.oatelauser.springplus.security.annotation.RequiresAdminRole;
import io.github.oatelauser.springplus.security.annotation.RequiresRole;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.security.config.core.GrantedAuthorityDefaults;
import org.springframework.util.ObjectUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static io.github.oatelauser.springplus.boot.utils.AnnotationUtils.findMergedMethodAnnotation;
import static org.springframework.core.annotation.AnnotatedElementUtils.findMergedAnnotation;


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
public class RequiresRoleAuthorizer extends GrantedAuthorityAuthorizer implements ApplicationListener<ApplicationReadyEvent> {

    private final GrantedAuthorityDefaults authorityDefaults;

    public RequiresRoleAuthorizer(ObjectProvider<GrantedAuthorityDefaults> authorityDefaults) {
        this.authorityDefaults = authorityDefaults.getIfAvailable(() -> new GrantedAuthorityDefaults("ROLE_"));
    }

    /**
     * 便利构造：默认 {@code ROLE_} 前缀（测试与手工装配用）
     */
    public RequiresRoleAuthorizer() {
        this.authorityDefaults = new GrantedAuthorityDefaults("ROLE_");
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

    // ========================= 启动期校验（fail-closed / CWE-862） =========================

    /**
     * 容器就绪后扫描全部 Bean 的 {@code @RequiresRole}（类级 + 方法级，含元注解归并）：
     * {@code role = {}} 属"配了等于没配"的配置错误——启动期失败优于运行期静默放行/403 之谜。
     */
    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        ConfigurableApplicationContext applicationContext = event.getApplicationContext();
        for (String beanName : applicationContext.getBeanDefinitionNames()) {
            Class<?> beanType = applicationContext.getType(beanName);
            if (beanType != null) {
                this.validateRequiresRole(beanType);
            }
        }
    }

    /** 校验单个类上的 {@code @RequiresRole}（类级 + 方法级），公开供测试直接调用 */
    public void validateRequiresRole(Class<?> beanType) {
        RequiresRole classLevel = findMergedAnnotation(beanType, RequiresRole.class);
        checkRole(beanType, null, classLevel);
        ReflectionUtils.doWithMethods(beanType, method -> checkRole(beanType,
                method, findMergedMethodAnnotation(method, RequiresRole.class, beanType)));
    }

    private static void checkRole(Class<?> beanType, Method method, RequiresRole requiresRole) {
        if (requiresRole != null && requiresRole.role().length == 0) {
            throw new IllegalStateException(location(beanType, method)
                    + "@RequiresRole 的 role 不能为空（空角色即放行所有已认证用户的配置错误，fail-closed 拒绝启动）");
        }
    }

    static String location(Class<?> beanType, Method method) {
        return method == null ? "类[" + beanType.getName() + "] " : "类["
                + beanType.getName() + "]方法[" + method.getName() + "] ";
    }

}
