package io.github.oatelauser.springplus.governor.autoconfigure;

import io.github.oatelauser.springplus.governor.idempotent.*;
import org.springframework.aop.Advisor;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * spring-plus-governor-starter 自动配置类
 * <p>
 * 服务治理域装配：幂等提交（{@code @Idempotent}）与防重复提交（{@code @RepeatSubmit}），
 * 编程式 AOP 织入（MethodInterceptor + DefaultPointcutAdvisor），不依赖 MVC 拦截器注册。
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2026-09-16
 * @since 1.0
 */
@AutoConfiguration
public class SpringPlusGovernorAutoConfiguration {

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(org.aopalliance.intercept.MethodInterceptor.class)
    static class IdempotentConfiguration {

        @Bean
        @ConditionalOnMissingBean
        IdempotentKeyStrategy fingerprintKeyStrategy() {
            return new FingerprintKeyStrategy();
        }

        @Bean
        @ConditionalOnMissingBean
        KeyStrategyResolver keyStrategyResolver(BeanFactory beanFactory) {
            return new KeyStrategyResolver(beanFactory);
        }

        @Bean
        @ConditionalOnMissingBean(IdempotentStore.class)
        IdempotentStore idempotentStore(ConfigurableListableBeanFactory beanFactory) {
            // StringRedisTemplate 只在方法体内按类名探测，避免作为参数/返回类型出现在签名中，
            // 使无 Redis 依赖时本配置类仍可被安全反射（方法签名不会触发可选类型类加载）。
            // classpath 有该类但容器未装配（未引驱动/排除了 Redis 自动配置）时回落内存存储。
            try {
                Class.forName("org.springframework.data.redis.core.StringRedisTemplate");
                StringRedisTemplate template = beanFactory.getBeanProvider(StringRedisTemplate.class).getIfAvailable();
                if (template != null) {
                    return new RedisIdempotentStore(template);
                }
            } catch (ClassNotFoundException ignored) {
            }
            return new InMemoryIdempotentStore();
        }

        @Bean
        @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
        Advisor repeatSubmitAdvisor(IdempotentStore store, KeyStrategyResolver strategyResolver) {
            return new DefaultPointcutAdvisor(IdempotentPointcuts.repeatSubmit(),
                    new RepeatSubmitInterceptor(store, strategyResolver));
        }

        @Bean
        @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
        Advisor idempotentAdvisor(IdempotentStore store, KeyStrategyResolver strategyResolver) {
            return new DefaultPointcutAdvisor(IdempotentPointcuts.idempotent(),
                    new IdempotentInterceptor(store, strategyResolver));
        }
    }

}
