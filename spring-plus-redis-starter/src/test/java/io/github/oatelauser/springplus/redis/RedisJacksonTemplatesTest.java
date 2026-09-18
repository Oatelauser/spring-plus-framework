package io.github.oatelauser.springplus.redis;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Jackson 化模板工厂回归：类型内嵌 round-trip / 容器 mapper 防污染 / 装配结构 / 无 mapper 回退。
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2026-09-18
 * @since 1.1
 */
class RedisJacksonTemplatesTest {

    record Point(int x, String name) {
    }

    @Test
    void valueSerializerEmbedsTypeAndRoundTrips() {
        GenericJacksonJsonRedisSerializer serializer = RedisJacksonTemplates.jacksonValueSerializer(null);

        byte[] bytes = serializer.serialize(new Point(1, "a"));
        assertTrue(new String(bytes, StandardCharsets.UTF_8).contains("@class"),
                "Object 值必须内嵌类型信息，否则读回 LinkedHashMap");

        Object back = serializer.deserialize(bytes);
        assertEquals(new Point(1, "a"), assertInstanceOf(Point.class, back));
    }

    @Test
    void containerMapperStaysUntouched() {
        JsonMapper shared = JsonMapper.builder().build();
        String before = shared.writeValueAsString(new Point(2, "b"));

        GenericJacksonJsonRedisSerializer serializer = RedisJacksonTemplates.jacksonValueSerializer(shared);

        // 共享 mapper 输出前后一致：default typing 只作用于副本，绝不污染 HTTP 响应等使用方
        String after = shared.writeValueAsString(new Point(2, "b"));
        assertEquals(before, after);
        assertFalse(after.contains("@class"));

        // 副本自身仍嵌类型：容器约定（如日期格式）被继承，round-trip 不变
        Object back = serializer.deserialize(serializer.serialize(new Point(2, "b")));
        assertEquals(new Point(2, "b"), assertInstanceOf(Point.class, back));
    }

    @Test
    void templateWiringStringKeyAndJacksonValue() {
        RedisTemplate<String, Object> template = RedisJacksonTemplates.jacksonRedisTemplate(null, null);

        assertInstanceOf(StringRedisSerializer.class, template.getKeySerializer());
        assertInstanceOf(StringRedisSerializer.class, template.getHashKeySerializer());
        assertInstanceOf(GenericJacksonJsonRedisSerializer.class, template.getValueSerializer());
        assertSame(template.getValueSerializer(), template.getHashValueSerializer());
    }

    @Test
    void beanNameContract() {
        assertEquals("jacksonRedisTemplate", RedisJacksonTemplates.JACKSON_REDIS_TEMPLATE_BEAN_NAME);
    }

}
