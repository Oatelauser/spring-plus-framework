package io.github.oatelauser.springplus.governor.idempotent;

import org.springframework.beans.factory.BeanFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 注解 {@code strategy} Class → Spring Bean 实例解析器（带缓存）。
 * <p>
 * 避免每次请求都走 {@link BeanFactory#getBean(Class)}，缓存 class → instance 映射。
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2026-08-31
 * @since 1.2
 */
public class KeyStrategyResolver {

    private final BeanFactory beanFactory;
    private final Map<Class<? extends IdempotentKeyStrategy>, IdempotentKeyStrategy> cache = new ConcurrentHashMap<>();

    public KeyStrategyResolver(BeanFactory beanFactory) {
        this.beanFactory = beanFactory;
    }

    @SuppressWarnings("unchecked")
    public <T extends IdempotentKeyStrategy> T resolve(Class<T> strategyClass) {
        return (T) cache.computeIfAbsent(strategyClass,
                beanFactory::getBean);
    }

}
