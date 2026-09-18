package io.github.oatelauser.springplus.security.authorization;

import lombok.Getter;
import org.aopalliance.intercept.MethodInvocation;
import org.jspecify.annotations.Nullable;
import org.springframework.aop.Pointcut;
import org.springframework.aop.support.AopUtils;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.authorization.method.MethodInvocationResult;
import org.springframework.security.core.Authentication;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.List;
import java.util.function.Supplier;

import static io.github.oatelauser.springplus.security.authorization.AnnotationAuthorizationDecision.ALLOW;


/**
 * 处理注解的后置授权管理器
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2024-10-22
 * @since 1.0
 */
@Getter
public class CompositePostAuthorizationManager implements AuthorizationManager<MethodInvocationResult> {

    private final List<AnnotationAuthorizer> postAuthorizationManagers;

    public CompositePostAuthorizationManager(List<AnnotationAuthorizer> authorizationManagers) {
        this.postAuthorizationManagers = authorizationManagers.stream()
                .filter(authorizer -> !authorizer.preAuthorize()).toList();
    }

    @Override
    public AuthorizationResult authorize(Supplier<? extends @Nullable Authentication> supplier, MethodInvocationResult mir) {
        Authentication authentication = supplier.get();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AuthenticationCredentialsNotFoundException("Could not find original Authentication object");
        }

        MethodInvocation mi = mir.getMethodInvocation();
        Method method = mi.getMethod();
        Object target = mi.getThis();
        Class<?> targetClass = (target != null) ? target.getClass() : null;
        Method specificMethod = AopUtils.getMostSpecificMethod(method, targetClass);
        for (AnnotationAuthorizer authorizer : postAuthorizationManagers) {
            Annotation annotation = authorizer.processAuthorizedMethod(specificMethod, targetClass);
            if (annotation != null) {
                AnnotationMethodInvocation invocation = new AnnotationMethodInvocation(targetClass, specificMethod, annotation, mi, mir.getResult());
                AuthorizationResult authorization = authorizer.check(authentication, invocation);
                if (!authorization.isGranted()) {
                    return authorization;
                }
            }
        }
        return ALLOW;
    }

    public Pointcut forAllAnnotations() {
        return CompositeAuthorizationManager.forAllAnnotations(postAuthorizationManagers);
    }

}
