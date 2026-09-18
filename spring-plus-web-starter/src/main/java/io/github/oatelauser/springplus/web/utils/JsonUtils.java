package io.github.oatelauser.springplus.web.utils;

import io.github.oatelauser.springplus.boot.utils.ApplicationContextHolder;
import io.github.oatelauser.springplus.web.autoconfigure.SpringPlusWebAutoConfiguration.JacksonConfiguration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

/**
 * JSON工具类
 * <p>
 * 容器中存在 {@link JsonMapper} Bean 时，{@link #shared()} 返回容器实例，
 * 保证与 MVC 消息转换、统一错误输出使用同一套序列化行为；
 * 非 Spring 环境（或容器无该 Bean）时回退为自建实例。
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2026-01-20
 * @since 1.1
 */
public final class JsonUtils {

    private JsonUtils() {
    }

    /**
     * 全局JSON操作对象
     *
     * @return JSON操作对象
     * @see JacksonConfiguration#jacksonWebMapperBuilderCustomizer()
     */
    public static JsonMapper shared() {
        ApplicationContext context = ApplicationContextHolder.getApplicationContextOrNull();
        if (context != null) {
            ObjectProvider<JsonMapper> provider = context.getBeanProvider(JsonMapper.class);
            JsonMapper containerMapper = provider.getIfAvailable();
            if (containerMapper != null) {
                return containerMapper;
            }
        }
        return SharedWrapper.MAPPER;
    }

    public static <T> T readValue(String content, Class<T> type) {
        return shared().readValue(content, type);
    }

    public static <T> T readValue(byte[] content, Class<T> type) {
        return shared().readValue(content, type);
    }

    public static <T> T readValue(String content, TypeRef<T> type) {
        return shared().readValue(content, type);
    }

    public static <T> T readValue(byte[] content, TypeRef<T> type) {
        return shared().readValue(content, type);
    }

    public static <T> T convertValue(Object fromValue, Class<T> type) {
        return shared().convertValue(fromValue, type);
    }

    public static <T> T convertValue(Object fromValue, TypeRef<T> type) {
        return shared().convertValue(fromValue, type);
    }

    public static String writeValueAsString(Object value) {
        return shared().writeValueAsString(value);
    }

    public static byte[] writeValueAsBytes(Object value) {
        return shared().writeValueAsBytes(value);
    }

    public static abstract class TypeRef<T> extends TypeReference<T> {
    }

    private final static class SharedWrapper {
        public final static JsonMapper MAPPER = wrapped();

        public static JsonMapper wrapped() {
            JacksonConfiguration jacksonConfiguration = new JacksonConfiguration();
            List<JsonMapperBuilderCustomizer> customizers = List.of(jacksonConfiguration.jacksonWebMapperBuilderCustomizer());
            JsonMapper.Builder builder = jacksonConfiguration.jacksonWebMapperBuilder(customizers);
            return builder.build();
        }
    }

}
