package io.github.oatelauser.springplus.security.authorization;

import org.springframework.core.Ordered;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

/**
 * 处理注解的授权器
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2024-10-17
 * @since 1.0
 */
public interface AnnotationAuthorizer extends Ordered, Authorizer {

    /**
     * 支持的注解类型解析类型
     */
    Class<? extends Annotation>[] getAnnotationClass();

    /**
     * 处理授权方法获取注解信息
     *
     * @param specificMethod 授权方法
     * @param targetClass    目标方法所在类
     * @return 授权注解对象
     */
    Annotation processAuthorizedMethod(Method specificMethod, Class<?> targetClass);

    /**
     * 是否前置授权器：true-前置授权处理器 false-后置授权处理器
     */
    default boolean preAuthorize() {
        return true;
    }

    @Override
    default int getOrder() {
        return 0;
    }

}
