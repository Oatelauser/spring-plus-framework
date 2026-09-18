package io.github.oatelauser.springplus.security.authorization;

import io.github.oatelauser.springplus.security.annotation.PostAuthorize;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

import static io.github.oatelauser.springplus.boot.utils.AnnotationUtils.findMergedMethodAnnotation;


/**
 * * {@link PostAuthorize}注解的自定义后置授权器
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2024-10-22
 * @since 1.0
 */
@SuppressWarnings("unchecked")
public class CompositeAnnotationPostAuthorizer implements AnnotationAuthorizer, ApplicationContextAware {

    private ApplicationContext applicationContext;

    @Override
    public Class<? extends Annotation>[] getAnnotationClass() {
        return new Class[]{ PostAuthorize.class };
    }

    @Override
    public Annotation processAuthorizedMethod(Method specificMethod, Class<?> targetClass) {
        return findMergedMethodAnnotation(specificMethod, PostAuthorize.class, targetClass);
    }

    @Override
    public boolean preAuthorize() {
        return false;
    }

    @Override
    public AuthorizationResult verify(Authentication authentication, AnnotationMethodInvocation mi) {
        PostAuthorize annotation = (PostAuthorize) mi.getAnnotation();
        String beanName = annotation.beanName();
        if (StringUtils.hasText(beanName)) {
            return CompositeAnnotationAuthorizer.verify(beanName, authentication, mi, applicationContext);
        }

        Class<? extends Authorizer>[] beanClass = annotation.beanClass();
        if (!ObjectUtils.isEmpty(beanClass)) {
            return CompositeAnnotationAuthorizer.verify(beanClass, annotation.relation(),
                    authentication, mi, applicationContext);
        }
        throw new IllegalArgumentException("@PostAuthorize必须指定beanName或者beanClass属性");
    }

    @Override
    public void setApplicationContext(@NonNull ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

}
