package io.github.oatelauser.springplus.security.autoconfigure;

import io.github.oatelauser.springplus.security.advice.SecurityExceptionAdvice;
import io.github.oatelauser.springplus.security.authorization.*;
import io.github.oatelauser.springplus.web.autoconfigure.ExceptionHandlingAutoConfiguration;
import io.github.oatelauser.springplus.web.error.engine.ExceptionOutputEngine;
import lombok.RequiredArgsConstructor;
import org.springframework.aop.Pointcut;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;
import org.springframework.security.authorization.AuthorizationEventPublisher;
import org.springframework.security.authorization.method.AuthorizationInterceptorsOrder;
import org.springframework.security.authorization.method.AuthorizationManagerAfterMethodInterceptor;
import org.springframework.security.authorization.method.AuthorizationManagerBeforeMethodInterceptor;
import org.springframework.security.config.core.GrantedAuthorityDefaults;
import org.springframework.security.core.context.SecurityContextHolderStrategy;

import java.util.ArrayList;
import java.util.List;

/**
 * 拓展Spring Security的自动配置类
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2026-02-22
 * @since 1.0
 */
@AutoConfiguration(after = ExceptionHandlingAutoConfiguration.class)
@RequiredArgsConstructor
public class SpringPlusSecurityAutoConfiguration {

    private final ObjectProvider<SecurityContextHolderStrategy> strategyProvider;
    private final ObjectProvider<AuthorizationEventPublisher> eventPublisherProvider;

    /**
     * 角色授权认证器
     *
     * @param authorityDefaults 默认的权限前缀，默认是“ROLE_”
     */
    @Bean
    public RequiresRoleAuthorizer requiresRoleAuthorizer(ObjectProvider<GrantedAuthorityDefaults> authorityDefaults) {
        return new RequiresRoleAuthorizer(authorityDefaults);
    }

    /**
     * 权限授权认证器
     */
    @Bean
    public RequiresPermissionAuthorizer requiresPermissionAuthorizer() {
        return new RequiresPermissionAuthorizer();
    }


    /**
     * 自定义前置权限方法拦截器
     * <p>
     * 替代{@link org.springframework.security.access.prepost.PreAuthorize}机制
     *
     * @see io.github.oatelauser.springplus.security.annotation.Authorize
     */
    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    public AuthorizationManagerBeforeMethodInterceptor preAuthorizeMethodInterceptor(
            RequiresRoleAuthorizer requiresRoleAuthorizer,
            RequiresPermissionAuthorizer requiresPermissionAuthorizer,
            ObjectProvider<AnnotationAuthorizer> authorizationManagers) {
        CompositeAuthorizationManager authorizationManager = new CompositeAuthorizationManager(
                collectAuthorizers(requiresRoleAuthorizer, requiresPermissionAuthorizer, authorizationManagers));
        Pointcut pointcut = authorizationManager.forAllAnnotations();
        AuthorizationManagerBeforeMethodInterceptor interceptor =
                new AuthorizationManagerBeforeMethodInterceptor(pointcut, authorizationManager);
        strategyProvider.ifAvailable(interceptor::setSecurityContextHolderStrategy);
        eventPublisherProvider.ifAvailable(interceptor::setAuthorizationEventPublisher);
        interceptor.setOrder(AuthorizationInterceptorsOrder.PRE_AUTHORIZE.getOrder());
        return interceptor;
    }

    /**
     * 自定义后置权限方法拦截器
     * <p>
     * 替代{@link org.springframework.security.access.prepost.PostAuthorize}机制
     *
     * @see io.github.oatelauser.springplus.security.annotation.PostAuthorize
     */
    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    public AuthorizationManagerAfterMethodInterceptor postAuthorizeMethodInterceptor(
            RequiresRoleAuthorizer requiresRoleAuthorizer,
            RequiresPermissionAuthorizer requiresPermissionAuthorizer,
            ObjectProvider<AnnotationAuthorizer> authorizationManagers) {
        CompositePostAuthorizationManager authorizationManager = new CompositePostAuthorizationManager(
                collectAuthorizers(requiresRoleAuthorizer, requiresPermissionAuthorizer, authorizationManagers));
        Pointcut pointcut = authorizationManager.forAllAnnotations();
        AuthorizationManagerAfterMethodInterceptor interceptor =
                new AuthorizationManagerAfterMethodInterceptor(pointcut, authorizationManager);
        strategyProvider.ifAvailable(interceptor::setSecurityContextHolderStrategy);
        eventPublisherProvider.ifAvailable(interceptor::setAuthorizationEventPublisher);
        interceptor.setOrder(AuthorizationInterceptorsOrder.POST_AUTHORIZE.getOrder());
        return interceptor;
    }

    /**
     * 汇总授权器：内置两个（显式参数，保证存在且确定序）+ 业务扩展（provider 流式收集）。
     * <p>
     * 此前 {@code ObjectProvider<List>} 解析：advisor 检索触发早实例化时可能解析为空列表，
     * 经 {@code forAnnotations(空)} 得到 null pointcut 使拦截器构造崩溃；显式参数根除该时序问题。
     */
    private static List<AnnotationAuthorizer> collectAuthorizers(RequiresRoleAuthorizer roleAuthorizer,
            RequiresPermissionAuthorizer permissionAuthorizer, ObjectProvider<AnnotationAuthorizer> provider) {
        List<AnnotationAuthorizer> authorizers = new ArrayList<>(
                provider.stream().filter(a -> a != roleAuthorizer && a != permissionAuthorizer).toList());
        authorizers.add(roleAuthorizer);
        authorizers.add(permissionAuthorizer);
        return authorizers;
    }

    /**
     * security 模块级异常 advice（denied 透传 + 认证异常映射 web 错误码）。
     * <p>
     * 仅 SERVLET Web 应用且 web 错误引擎就位时装配（{@code after = ExceptionHandlingAutoConfiguration}
     * 保证引擎 Bean 定义先注册，条件求值确定）；非 Web 应用不引入任何 MVC 依赖。
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnBean(ExceptionOutputEngine.class)
    static class SecurityExceptionAdviceConfiguration {

        @Bean
        public SecurityExceptionAdvice securityExceptionAdvice(ExceptionOutputEngine outputEngine) {
            return new SecurityExceptionAdvice(outputEngine);
        }

    }

}
