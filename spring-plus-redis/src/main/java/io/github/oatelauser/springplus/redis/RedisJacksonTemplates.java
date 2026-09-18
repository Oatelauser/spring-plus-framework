package io.github.oatelauser.springplus.redis;

import org.jspecify.annotations.Nullable;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import tools.jackson.databind.jsontype.PolymorphicTypeValidator;

/**
 * Jackson 化 {@link RedisTemplate} 工厂（门面按 Q4 命名规范：{@code Redis<能力><形态>}）。
 * <p>
 * Spring Boot 自动配置只给 JDK 序列化的 {@code redisTemplate} 与 {@code stringRedisTemplate}，
 * "Jackson 化模板"是生态里被复制粘贴最多的配置段——本类把它收编为家族能力，消掉三类
 * 经典手写错误：
 * <ol>
 *   <li><b>忘嵌类型信息</b>：Object 值读回 {@code LinkedHashMap} 抛 ClassCastException——
 *       本工厂以 {@code DefaultTyping.NON_FINAL} + {@code Id.CLASS} 在 JSON 中内嵌 {@code @class}，
 *       Object 值读写 round-trip 安全；</li>
 *   <li><b>共享 mapper 被污染</b>：把容器 JsonMapper 原样交给序列化器再激活 default typing，
 *       会连带 HTTP 响应等所有使用方开始输出 {@code @class}——本工厂一律
 *       {@link ObjectMapper#rebuild()} 出<b>副本</b>再配置，容器 mapper 分毫不动；</li>
 *   <li><b>泛型/序列化器拼装错漏</b>：key 与 hashKey 统一 String 序列化，value 与 hashValue
 *       统一 Jackson 序列化，一把装配。</li>
 * </ol>
 * <p>
 * {@code source} 传容器中的 {@link JsonMapper}（建议）：Redis 值与 web 响应共享同一套
 * 序列化约定（日期格式等家族一致）；容器没有时回退自建。
 * <p>
 * <b>安全红线</b>：家族缺省类型校验器与 Spring Data Redis builder 缺省一致（信任域 =
 * 本应用写入的 Redis）。若 Redis 可被不可信方写入，请自行收紧
 * {@code PolymorphicTypeValidator} 后调用 {@code jacksonValueSerializer} 装配——
 * 无约束的多态反序列化面对不可信数据是任意代码执行风险（OWASP）。
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2026-09-18
 * @since 1.1
 */
public final class RedisJacksonTemplates {

    /** 自动配置注册的 Bean 名（按名注入：{@code @Qualifier("jacksonRedisTemplate")}） */
    public static final String JACKSON_REDIS_TEMPLATE_BEAN_NAME = "jacksonRedisTemplate";

    /** 信任域 = 本应用写入的 Redis；与 SDR builder 缺省一致，别处写入需自行收紧 */
    static final PolymorphicTypeValidator FAMILY_TYPE_VALIDATOR = BasicPolymorphicTypeValidator.builder()
            .allowIfBaseType(Object.class)
            .allowIfSubType((ctx, clazz) -> true)
            .build();

    private RedisJacksonTemplates() {
    }

    /**
     * Jackson 值序列化器：JSON 内嵌 {@code @class}，对容器 mapper 取副本配置，原 mapper 不被污染。
     */
    public static GenericJacksonJsonRedisSerializer jacksonValueSerializer(@Nullable JsonMapper source) {
        JsonMapper.Builder builder = source != null ? source.rebuild() : JsonMapper.builder();
        return GenericJacksonJsonRedisSerializer.builder(() -> builder)
                .enableDefaultTyping(FAMILY_TYPE_VALIDATOR)
                .build();
    }

    /**
     * Jackson 化模板：{@code RedisTemplate<String, Object>}，String key + Jackson value（hash 同构）。
     * <p>
     * 不接管 Boot 的 {@code redisTemplate}（JDK 序列化）——按名注入，零惊吓；存量 JDK 数据不受影响。
     */
    public static RedisTemplate<String, Object> jacksonRedisTemplate(RedisConnectionFactory connectionFactory,
            @Nullable JsonMapper source) {
        GenericJacksonJsonRedisSerializer valueSerializer = jacksonValueSerializer(source);
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(RedisSerializer.string());
        template.setHashKeySerializer(RedisSerializer.string());
        template.setValueSerializer(valueSerializer);
        template.setHashValueSerializer(valueSerializer);
        return template;
    }

}
