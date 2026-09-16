package io.github.oatelauser.springplus.security.authorization;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.aopalliance.intercept.MethodInvocation;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

/**
 * 注解信息的方法调用
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2024-10-18
 * @since 1.0
 */
@Data
@RequiredArgsConstructor
@SuppressWarnings("ClassCanBeRecord")
public class AnnotationMethodInvocation {

    /**
     * 真正的目标类
     */
    private final Class<?> targetClass;

    /**
     * 真正的目标方法
     */
    private final Method specificMethod;

    /**
     * 解析方法上的注解对象
     */
    private final Annotation annotation;

    /**
     * 原始的方法调用信息
     */
    private final MethodInvocation invocation;

    /**
     * 只有后置授权可以获取方法返回值
     */
    private final Object result;

}
