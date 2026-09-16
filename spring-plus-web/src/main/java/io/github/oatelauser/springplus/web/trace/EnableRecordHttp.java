package io.github.oatelauser.springplus.web.trace;

import io.github.oatelauser.springplus.web.trace.AbstractHttpTraceFilter;
import io.github.oatelauser.springplus.web.trace.LoggingHttpTraceFilter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;

import java.lang.annotation.*;

import static org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type.SERVLET;

/**
 * 启用 HTTP 请求/响应日志记录。
 * <p>
 * 在任意 {@code @Configuration} 类上标注此注解即可激活 {@link LoggingHttpTraceFilter}
 *
 * <pre>{@code
 * @EnableRecordHttp
 * @SpringBootApplication
 * public class Application {
 *     public static void main(String[] args) {
 *         SpringApplication.run(Application.class, args);
 *     }
 * }
 * }</pre>
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2026-02-24
 * @see RecordHttpConfiguration
 * @since 1.0
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Import(EnableRecordHttp.RecordHttpConfiguration.class)
public @interface EnableRecordHttp {

    /**
     * HTTP 请求/响应记录器自动配置
     *
     * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
     * @date 2026-02-24
     * @see LoggingHttpTraceFilter
     * @since 1.0
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = SERVLET)
    class RecordHttpConfiguration {
        @Bean
        @ConditionalOnMissingBean(AbstractHttpTraceFilter.class)
        public LoggingHttpTraceFilter loggingHttpRecorderFilter() {
            return new LoggingHttpTraceFilter();
        }

        @Bean
        @ConditionalOnMissingBean(name = "httpRecorderFilterRegistration")
        public FilterRegistrationBean<AbstractHttpTraceFilter> httpRecorderFilterRegistration(
                AbstractHttpTraceFilter filter) {
            FilterRegistrationBean<AbstractHttpTraceFilter> registration = new FilterRegistrationBean<>();
            registration.setFilter(filter);
            registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
            return registration;
        }
    }

}
