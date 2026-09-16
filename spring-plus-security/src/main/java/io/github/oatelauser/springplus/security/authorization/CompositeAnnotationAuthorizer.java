package io.github.oatelauser.springplus.security.authorization;

import io.github.oatelauser.springplus.security.annotation.Authorize;
import io.github.oatelauser.springplus.security.annotation.Relation;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashSet;

import static io.github.oatelauser.springplus.security.authorization.AnnotationAuthorizationDecision.ALLOW;
import static io.github.oatelauser.springplus.web.utils.AnnotationUtils.findMergedMethodAnnotation;


/**
 * {@link Authorize}注解的自定义授权器
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2024-10-17
 * @since 1.0
 */
@Component
@SuppressWarnings("unchecked")
public class CompositeAnnotationAuthorizer implements AnnotationAuthorizer, ApplicationContextAware {

    private ApplicationContext applicationContext;

    @Override
    public Class<? extends Annotation>[] getAnnotationClass() {
        return new Class[]{ Authorize.class };
    }

    @Override
    public Annotation processAuthorizedMethod(Method specificMethod, Class<?> targetClass) {
        return findMergedMethodAnnotation(specificMethod, Authorize.class, targetClass);
    }

    @Override
    public AuthorizationResult verify(Authentication authentication, AnnotationMethodInvocation mi) {
        Authorize annotation = (Authorize) mi.getAnnotation();
        String beanName = annotation.beanName();
        if (StringUtils.hasText(beanName)) {
            return verify(beanName, authentication, mi, applicationContext);
        }

        Class<? extends Authorizer>[] beanClass = annotation.beanClass();
        if (!ObjectUtils.isEmpty(beanClass)) {
            return verify(beanClass, annotation.relation(), authentication, mi, applicationContext);
        }
        throw new IllegalArgumentException("@Authorize必须指定beanName或者beanClass属性");
    }

    static AuthorizationResult verify(String beanName, Authentication authentication,
            AnnotationMethodInvocation mi, ApplicationContext applicationContext) {
        Object bean = applicationContext.getBean(beanName);
        if (bean instanceof Authorizer authorizer) {
            return authorizer.check(authentication, mi);
        }
        throw new ClassCastException("指定BeanName[" + beanName + "]没有实现" + Authorizer.class);
    }

    static AuthorizationResult verify(Class<? extends Authorizer>[] beanClass, Relation relation,
            Authentication authentication, AnnotationMethodInvocation mi,
            ApplicationContext applicationContext) {
        switch (relation) {
            case OR -> {
                Collection<GrantedAuthority> authorities = new HashSet<>();
                for (Class<? extends Authorizer> type : beanClass) {
                    AuthorizationResult authorization = applicationContext.getBean(type).check(authentication, mi);
                    if (authorization.isGranted()) {
                        return authorization;
                    }
                    if (authentication instanceof AnnotationAuthorizationDecision decision &&
                            !CollectionUtils.isEmpty(decision.getAuthorities())) {
                        authorities.addAll(decision.getAuthorities());
                    }
                }
                return AnnotationAuthorizationDecision.deny(authorities);
            }
            case AND -> {
                for (Class<? extends Authorizer> type : beanClass) {
                    AuthorizationResult authorization = applicationContext.getBean(type).check(authentication, mi);
                    if (!authorization.isGranted()) {
                        return authorization;
                    }
                }
                return ALLOW;
            }
        }
        throw new UnsupportedOperationException();
    }

    @Override
    public void setApplicationContext(@NonNull ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Override
    public int getOrder() {
        return 1;
    }

}
