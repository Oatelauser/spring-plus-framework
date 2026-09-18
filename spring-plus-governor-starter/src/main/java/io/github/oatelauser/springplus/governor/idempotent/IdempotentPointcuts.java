package io.github.oatelauser.springplus.governor.idempotent;

import io.github.oatelauser.springplus.governor.annotation.Idempotent;
import io.github.oatelauser.springplus.governor.annotation.RepeatSubmit;
import org.jspecify.annotations.NonNull;
import org.springframework.aop.support.StaticMethodMatcherPointcut;
import org.springframework.core.annotation.AnnotatedElementUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

/** 幂等注解的静态方法切点。 */
public final class IdempotentPointcuts {

    private IdempotentPointcuts() {
    }

    public static StaticMethodMatcherPointcut repeatSubmit() {
        return pointcut(RepeatSubmit.class);
    }

    public static StaticMethodMatcherPointcut idempotent() {
        return pointcut(Idempotent.class);
    }

    private static StaticMethodMatcherPointcut pointcut(Class<? extends Annotation> annotationType) {
        return new StaticMethodMatcherPointcut() {
            @Override
            public boolean matches(@NonNull Method method, @NonNull Class<?> targetClass) {
                return AnnotatedElementUtils.findMergedAnnotation(method, annotationType) != null
                        || AnnotatedElementUtils.findMergedAnnotation(targetClass, annotationType) != null;
            }
        };
    }

}
