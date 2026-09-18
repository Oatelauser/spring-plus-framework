package io.github.oatelauser.springplus.web.autoconfigure;

import io.github.oatelauser.springplus.web.trace.AnnotationHandlerMethodPostProcessor;
import io.github.oatelauser.springplus.web.stream.HttpResponseWriter;
import io.github.oatelauser.springplus.web.stream.HttpWriterFactory;
import io.github.oatelauser.springplus.boot.utils.ApplicationContextHolder;
import io.github.oatelauser.springplus.web.utils.JsonUtils;
import io.github.oatelauser.springplus.web.validation.clazz.ClassValidatorPostProcessor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Scope;
import org.springframework.core.env.Environment;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.core.util.JsonRecyclerPools;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.ext.javatime.deser.LocalDateDeserializer;
import tools.jackson.databind.ext.javatime.deser.LocalDateTimeDeserializer;
import tools.jackson.databind.ext.javatime.deser.LocalTimeDeserializer;
import tools.jackson.databind.ext.javatime.ser.LocalDateSerializer;
import tools.jackson.databind.ext.javatime.ser.LocalDateTimeSerializer;
import tools.jackson.databind.ext.javatime.ser.LocalTimeSerializer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.std.ToStringSerializer;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.TimeZone;

import static tools.jackson.databind.cfg.DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS;
import static tools.jackson.databind.cfg.DateTimeFeature.WRITE_DURATIONS_AS_TIMESTAMPS;

/**
 * spring-plus-web-starter 自动配置类
 * <p>
 * 统一错误处理的全部 Bean（引擎 / 协议探测 / 规则扫描 / Mapper 链 / 三协议渲染器 / SSE 工具 /
 * {@code GlobalExceptionAdvice}）已迁至 {@link ExceptionHandlingAutoConfiguration}——按关注点拆分，
 * 错误体系在 starter 语义下自成一体。
 * <p>
 * {@code before} 官方 Jackson 自动配置：显式固化本模块的 {@code JsonMapper.Builder} 优先注册
 * （两者同为 {@code @ConditionalOnMissingBean}，不声明顺序时依赖注册次序的不确定行为）。
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2026-09-16
 * @since 1.0
 */
@AutoConfiguration(before = JacksonAutoConfiguration.class)
@Import(RequestMappingHandlerAdapterConfiguration.class)
public class SpringPlusWebAutoConfiguration {

    @Bean
    public ApplicationContextHolder applicationContextHolder() {
        return new ApplicationContextHolder();
    }

    @Bean
    public HttpResponseWriter webServiceResponse(RequestMappingHandlerAdapter requestMappingHandlerAdapter) {
        return new HttpResponseWriter(requestMappingHandlerAdapter);
    }

    /**
     * 流式写入器统一工厂：SSE / NDJSON / Chunk / 文件下载的协议头在此集中配置。
     * <p>
     * {@code JsonMapper} 注入优先（容器若有则用之），否则兜底 {@link JsonUtils#shared()} 全局实例。
     * <p>
     * 注入 {@link RequestMappingHandlerAdapter} 后，{@code writeChunk(Object)} /
     * {@code writeLine(Object)} 走 MVC 消息转换器矩阵序列化，业务注册的自定义转换器
     * 对流式写入器同样生效（注入方式同 {@link #webServiceResponse}）。
     */
    @Bean
    public HttpWriterFactory streamWriterFactory(ObjectProvider<JsonMapper> jsonMapper,
            RequestMappingHandlerAdapter requestMappingHandlerAdapter) {
        return new HttpWriterFactory(jsonMapper.getIfAvailable(JsonUtils::shared),
                requestMappingHandlerAdapter);
    }

    @Bean
    public AnnotationHandlerMethodPostProcessor annotationHandlerMethodPostProcessor() {
        return new AnnotationHandlerMethodPostProcessor();
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(LocalValidatorFactoryBean.class)
    static class SpringValidationConfiguration {

        @Bean
        ClassValidatorPostProcessor classValidatorPostProcessor(
                @Value("${spring-plus.web.validation.fail-fast:true}") boolean failFast) {
            return new ClassValidatorPostProcessor(failFast);
        }

    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(JsonMapperBuilderCustomizer.class)
    public static class JacksonConfiguration {

        public static final String DEFAULT_TIME_FORMAT = "HH:mm:ss";
        public static final String DEFAULT_DATE_FORMAT = "yyyy-MM-dd";
        public static final String DEFAULT_DATETIME_PATTERN = "yyyy-MM-dd HH:mm:ss";

        @Bean
        @ConditionalOnMissingBean
        @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
        public JsonMapper.Builder jacksonWebMapperBuilder(List<JsonMapperBuilderCustomizer> customizers) {
            JsonFactory jsonFactory = JsonFactory.builder()
                    .recyclerPool(JsonRecyclerPools.threadLocalPool())
                    .build();
            JsonMapper.Builder builder = JsonMapper.builder(jsonFactory);
            customizers.forEach(customizer ->
                    customizer.customize(builder));
            return builder;
        }

        @Bean
        public JsonMapperBuilderCustomizer jacksonWebMapperBuilderCustomizer(Environment environment) {
            return jacksonWebMapperBuilderCustomizer(
                    environment.getProperty("spring.jackson.time-zone", ""),
                    environment.getProperty("spring.jackson.datetime-format", DEFAULT_DATETIME_PATTERN),
                    environment.getProperty("spring.jackson.date-format", DEFAULT_DATE_FORMAT),
                    environment.getProperty("spring.jackson.time-format", DEFAULT_TIME_FORMAT),
                    environment.getProperty("spring.jackson.long-to-string", Boolean.class, false));
        }

        /**
         * @param timeZone        时区 ID（如 GMT+8），空串 = 跟随 JVM 默认
         * @param datetimeFormat  LocalDateTime 序列化格式
         * @param dateFormat      LocalDate 与 java.util.Date 序列化格式（与官方键语义一致）
         * @param timeFormat      LocalTime 序列化格式
         * @param longToString    Long 序列化为字符串（防 JS 精度丢失），默认关闭
         */
        public static JsonMapperBuilderCustomizer jacksonWebMapperBuilderCustomizer(
                String timeZone, String datetimeFormat, String dateFormat, String timeFormat, boolean longToString) {
            return builder -> {
                builder
                        .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
                        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                        .defaultDateFormat(new SimpleDateFormat(dateFormat))
                        .enable(WRITE_DATES_AS_TIMESTAMPS, WRITE_DURATIONS_AS_TIMESTAMPS)
                        .addModule(jacksonWebModule(datetimeFormat, dateFormat, timeFormat));
                if (longToString) {
                    builder.addModule(new SimpleModule()
                            .addSerializer(Long.class, ToStringSerializer.instance)
                            .addSerializer(Long.TYPE, ToStringSerializer.instance));
                }
                if (!timeZone.isBlank()) {
                    builder.defaultTimeZone(TimeZone.getTimeZone(timeZone));
                }
            };
        }

        public JsonMapperBuilderCustomizer jacksonWebMapperBuilderCustomizer() {
            return jacksonWebMapperBuilderCustomizer("", DEFAULT_DATETIME_PATTERN,
                    DEFAULT_DATE_FORMAT, DEFAULT_TIME_FORMAT, false);
        }

        private static JacksonModule jacksonWebModule(String datetimeFormat, String dateFormat, String timeFormat) {
            return new SimpleModule()
                    .addDeserializer(LocalDateTime.class, new LocalDateTimeDeserializer(DateTimeFormatter.ofPattern(datetimeFormat)))
                    .addDeserializer(LocalDate.class, new LocalDateDeserializer(DateTimeFormatter.ofPattern(dateFormat)))
                    .addDeserializer(LocalTime.class, new LocalTimeDeserializer(DateTimeFormatter.ofPattern(timeFormat)))
                    .addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(DateTimeFormatter.ofPattern(datetimeFormat)))
                    .addSerializer(LocalDate.class, new LocalDateSerializer(DateTimeFormatter.ofPattern(dateFormat)))
                    .addSerializer(LocalTime.class, new LocalTimeSerializer(DateTimeFormatter.ofPattern(timeFormat)));
        }

    }

}
