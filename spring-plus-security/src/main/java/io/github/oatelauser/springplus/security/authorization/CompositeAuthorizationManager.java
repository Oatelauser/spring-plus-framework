package io.github.oatelauser.springplus.security.authorization;

import lombok.Getter;
import org.aopalliance.intercept.MethodInvocation;
import org.jspecify.annotations.Nullable;
import org.springframework.aop.Pointcut;
import org.springframework.aop.support.AopUtils;
import org.springframework.aop.support.ComposablePointcut;
import org.springframework.aop.support.Pointcuts;
import org.springframework.aop.support.annotation.AnnotationMatchingPointcut;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.util.Assert;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import static io.github.oatelauser.springplus.security.authorization.AnnotationAuthorizationDecision.ALLOW;

/**
 * 处理注解的授权管理器
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2024-10-17
 * @since 1.0
 */
@Getter
@SuppressWarnings("ClassCanBeRecord")
public class CompositeAuthorizationManager implements AuthorizationManager<MethodInvocation> {

    private final List<AnnotationAuthorizer> authorizationManagers;

    public CompositeAuthorizationManager(List<AnnotationAuthorizer> authorizationManagers) {
        this.authorizationManagers = authorizationManagers.stream()
                .filter(AnnotationAuthorizer::preAuthorize).toList();
    }

    @Override
    public AuthorizationResult authorize(Supplier<? extends @Nullable Authentication> supplier, MethodInvocation mi) {
        Authentication authentication = supplier.get();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AuthenticationCredentialsNotFoundException("Could not find original Authentication object");
        }

        Method method = mi.getMethod();
        Object target = mi.getThis();
        Class<?> targetClass = (target != null) ? target.getClass() : null;
        Method specificMethod = AopUtils.getMostSpecificMethod(method, targetClass);
        for (AnnotationAuthorizer authorizer : authorizationManagers) {
            Annotation annotation = authorizer.processAuthorizedMethod(specificMethod, targetClass);
            if (annotation != null) {
                AnnotationMethodInvocation invocation = new AnnotationMethodInvocation(targetClass, specificMethod, annotation, mi, null);
                AuthorizationResult authorization = authorizer.check(authentication, invocation);
                if (!authorization.isGranted()) {
                    return authorization;
                }
            }
        }
        return ALLOW;
    }

    public Pointcut forAllAnnotations() {
        return forAllAnnotations(authorizationManagers);
    }

    static Pointcut forAllAnnotations(List<AnnotationAuthorizer> authorizationManagers) {
        Set<Class<? extends Annotation>> annotationSet = new HashSet<>();
        for (AnnotationAuthorizer authorizer : authorizationManagers) {
            Class<? extends Annotation>[] annotationClass = authorizer.getAnnotationClass();
            Assert.notEmpty(annotationClass, "注解认证器必须有支持的注解类型");
            Assert.noNullElements(annotationClass, "注解类型class不能为空");
            for (Class<? extends Annotation> type : annotationClass) {
                if (!annotationSet.add(type)) {
                    throw new IllegalStateException("重复的注解[" + type + "]处理授权器");
                }
            }
        }

        return forAnnotations(annotationSet);
    }

    static Pointcut forAnnotations(Collection<Class<? extends Annotation>> annotations) {
        ComposablePointcut pointcut = null;
        for (Class<? extends Annotation> annotation : annotations) {
            if (pointcut == null) {
                pointcut = new ComposablePointcut(classOrMethod(annotation));
            } else {
                pointcut.union(classOrMethod(annotation));
            }
        }
        return pointcut;
    }

    private static Pointcut classOrMethod(Class<? extends Annotation> annotation) {
        return Pointcuts.union(new AnnotationMatchingPointcut(null, annotation, true),
                new AnnotationMatchingPointcut(annotation, true));
    }

}
