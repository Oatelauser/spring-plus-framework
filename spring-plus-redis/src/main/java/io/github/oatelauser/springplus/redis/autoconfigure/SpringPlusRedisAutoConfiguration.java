package io.github.oatelauser.springplus.redis.autoconfigure;

import io.github.oatelauser.springplus.redis.RedisJacksonTemplates;
import io.github.oatelauser.springplus.redis.RedisStringOperation;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import tools.jackson.databind.json.JsonMapper;

/**
 * spring-plus-redis 自动配置（ADR 0003：自 boot 模块拆出的独立能力域）。
 * <p>
 * classpath 无 spring-data-redis 时整体退避；消费方注册同名
 * {@link RedisStringOperation} / {@code jacksonRedisTemplate} Bean 即可替换默认实现。
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2026-09-18
 * @since 1.1
 */
@AutoConfiguration
public class SpringPlusRedisAutoConfiguration {

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(StringRedisTemplate.class)
    static class RedisConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public RedisStringOperation redisStringOperation(StringRedisTemplate redisTemplate) {
            return new RedisStringOperation(redisTemplate);
        }

    }

    /**
     * Jackson 化 {@code RedisTemplate<String, Object>}（Boot 只给 JDK 序列化版，此处补齐）：
     * JSON 内嵌 {@code @class}（Object 值 round-trip 安全）、容器 JsonMapper 取副本不被污染。
     * <p>
     * 命名 Bean，不接管 Boot 的 {@code redisTemplate}——按名注入，存量 JDK 序列化数据零影响。
     * classpath 无 Jackson 时整体退避。
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass({GenericJacksonJsonRedisSerializer.class, JsonMapper.class})
    static class JacksonTemplateConfiguration {

        @Bean(name = RedisJacksonTemplates.JACKSON_REDIS_TEMPLATE_BEAN_NAME)
        @ConditionalOnMissingBean(name = RedisJacksonTemplates.JACKSON_REDIS_TEMPLATE_BEAN_NAME)
        public RedisTemplate<String, Object> jacksonRedisTemplate(RedisConnectionFactory connectionFactory,
                ObjectProvider<JsonMapper> jsonMapper) {
            return RedisJacksonTemplates.jacksonRedisTemplate(connectionFactory, jsonMapper.getIfAvailable());
        }

    }

}
