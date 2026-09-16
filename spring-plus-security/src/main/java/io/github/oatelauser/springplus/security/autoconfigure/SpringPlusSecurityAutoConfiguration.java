package io.github.oatelauser.springplus.security.autoconfigure;

import io.github.oatelauser.springplus.security.authorization.*;
import lombok.RequiredArgsConstructor;
import org.springframework.aop.Pointcut;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Role;
import org.springframework.security.authorization.AuthorizationEventPublisher;
import org.springframework.security.authorization.method.AuthorizationInterceptorsOrder;
import org.springframework.security.authorization.method.AuthorizationManagerAfterMethodInterceptor;
import org.springframework.security.authorization.method.AuthorizationManagerBeforeMethodInterceptor;
import org.springframework.security.config.core.GrantedAuthorityDefaults;
import org.springframework.security.core.context.SecurityContextHolderStrategy;

import java.util.List;

/**
 * 拓展Spring Security的自动配置类
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2026-02-22
 * @since 1.0
 */
@AutoConfiguration
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
            ObjectProvider<List<AnnotationAuthorizer>> authorizationManagers) {
        CompositeAuthorizationManager authorizationManager = new CompositeAuthorizationManager(
                authorizationManagers.getIfAvailable(List::of));
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
            ObjectProvider<List<AnnotationAuthorizer>> authorizationManagers) {
        CompositePostAuthorizationManager authorizationManager =  new CompositePostAuthorizationManager(
                authorizationManagers.getIfAvailable(List::of));
        Pointcut pointcut = authorizationManager.forAllAnnotations();
        AuthorizationManagerAfterMethodInterceptor interceptor =
                new AuthorizationManagerAfterMethodInterceptor(pointcut, authorizationManager);
        strategyProvider.ifAvailable(interceptor::setSecurityContextHolderStrategy);
        eventPublisherProvider.ifAvailable(interceptor::setAuthorizationEventPublisher);
        interceptor.setOrder(AuthorizationInterceptorsOrder.POST_AUTHORIZE.getOrder());
        return interceptor;
    }

}
