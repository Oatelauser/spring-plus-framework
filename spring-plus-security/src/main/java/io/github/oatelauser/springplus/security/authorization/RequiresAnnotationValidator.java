package io.github.oatelauser.springplus.security.authorization;

import io.github.oatelauser.springplus.security.annotation.RequiresPermission;
import io.github.oatelauser.springplus.security.annotation.RequiresRole;
import io.github.oatelauser.springplus.web.utils.AnnotationUtils;
import org.springframework.util.ClassUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;

/**
 * 授权注解启动期校验器（fail-closed，CWE-862）。
 * <p>
 * "配了等于没配"的注解必须在发布前暴露，而非运行期放行或 500：
 * <ul>
 *   <li>{@code @RequiresRole(role = {})} —— 空角色此前会被放行所有已认证用户，现启动失败</li>
 *   <li>{@code @RequiresPermission} 的 {@code source}/{@code action} 为空 —— 此前首次调用才 500，现启动失败</li>
 * </ul>
 * 扫描容器中全部 Bean（含 CGLIB 代理还原后的用户类）的方法级与类级注解。
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2026-09-16
 * @since 1.0.1
 */
public class RequiresAnnotationValidator implements SmartInitializingSingleton, ApplicationContextAware {

    private ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Override
    public void afterSingletonsInstantiated() {
        for (String beanName : this.applicationContext.getBeanDefinitionNames()) {
            Class<?> beanType = this.applicationContext.getType(beanName);
            if (beanType == null) {
                continue;
            }
            // CGLIB 代理还原用户类（Spring 7 已移除 AopUtils.isAopProxyClass）
            validate(ClassUtils.getUserClass(beanType));
        }
    }

    /**
     * 校验单个类上的授权注解（类级 + 方法级），可独立调用便于测试。
     *
     * @throws IllegalStateException 存在空配置注解
     */
    public void validate(Class<?> beanType) {
        checkRole(beanType, null, AnnotatedElementUtils.findMergedAnnotation(beanType, RequiresRole.class));
        checkPermission(beanType, null,
                AnnotatedElementUtils.findMergedAnnotation(beanType, RequiresPermission.class));

        ReflectionUtils.doWithMethods(beanType, method -> {
            checkRole(beanType, method,
                    AnnotationUtils.findMergedMethodAnnotation(method, RequiresRole.class, beanType));
            checkPermission(beanType, method,
                    AnnotationUtils.findMergedMethodAnnotation(method, RequiresPermission.class, beanType));
        });
    }

    private void checkRole(Class<?> beanType, Method method, RequiresRole requiresRole) {
        if (requiresRole != null && requiresRole.role().length == 0) {
            throw new IllegalStateException(location(beanType, method)
                    + "@RequiresRole 的 role 不能为空（空角色即放行所有已认证用户的配置错误，fail-closed 拒绝启动）");
        }
    }

    private void checkPermission(Class<?> beanType, Method method, RequiresPermission requiresPermission) {
        if (requiresPermission == null) {
            return;
        }
        if (!StringUtils.hasText(requiresPermission.source()) || !StringUtils.hasText(requiresPermission.action())) {
            throw new IllegalStateException(location(beanType, method)
                    + "@RequiresPermission 的 source/action 不能为空（启动期失败优于运行期 500）");
        }
    }

    private static String location(Class<?> beanType, Method method) {
        return method == null
                ? "类[" + beanType.getName() + "] "
                : "类[" + beanType.getName() + "]方法[" + method.getName() + "] ";
    }

}
