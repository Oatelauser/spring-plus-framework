package io.github.oatelauser.springplus.boot.autoconfigure;

import io.github.oatelauser.springplus.boot.client.ApiClient;
import io.github.oatelauser.springplus.boot.client.ApiClientSettings;
import io.github.oatelauser.springplus.boot.client.adapt.ClientHttpRequestFactoryProvider;
import io.github.oatelauser.springplus.boot.client.interceptor.AuthProvider;
import io.github.oatelauser.springplus.boot.client.metrics.ApiClientMetricsPostProcessor;
import io.github.oatelauser.springplus.boot.lifecycle.ShutdownHook;
import io.github.oatelauser.springplus.boot.lifecycle.SmartGracefulShutdownHandler;
import io.github.oatelauser.springplus.boot.lifecycle.StartupProcess;
import io.github.oatelauser.springplus.boot.lifecycle.WebServerPostProcessor;
import io.github.oatelauser.springplus.boot.process.HandleBeanPostProcessor;
import io.github.oatelauser.springplus.boot.process.HandleBeanPostProcessor.HandlerBean;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.util.List;

/**
 * spring-plus-boot-starter 自动配置类
 * <p>
 * Boot 生态装配能力域：Web 服务器生命周期（优雅停机 / 启动过程）、HTTP 客户端。
 * Redis 工具已按能力域拆出为 spring-plus-redis-starter（ADR 0003）。
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2026-09-16
 * @since 1.0
 */
@AutoConfiguration
public class SpringPlusBootAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public WebServerPostProcessor webServerPostProcessor(ObjectProvider<List<StartupProcess>> startupProcesses) {
        return new WebServerPostProcessor(startupProcesses);
    }

    @Bean
    @ConditionalOnMissingBean
    public SmartGracefulShutdownHandler smartGracefulShutdownHandler(ObjectProvider<List<ShutdownHook>> shutdownHooks) {
        return new SmartGracefulShutdownHandler(shutdownHooks);
    }

    /**
     * 启动期全量单例扫描设施：容器内所有 {@link HandlerBean} 在单例就绪后逐个处理 Bean
     * （典型用途：security 模块的注解配置启动校验）。容器无任何 {@link HandlerBean} 时不装配。
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(HandlerBean.class)
    public HandleBeanPostProcessor handleBeanPostProcessor(ObjectProvider<List<HandlerBean>> handlerBeans) {
        return new HandleBeanPostProcessor(handlerBeans.getIfAvailable(List::of));
    }

    /**
     * 默认 ApiClient 自动装配：仅当配置了 {@code spring-plus.client.base-url}
     * 且容器中没有业务自定义 {@code ApiClient} Bean 时生效；
     * 多客户端场景走 {@code ApiClient.builder()} 自行装配。
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(ApiClient.class)
    @EnableConfigurationProperties(ApiClientSettings.class)
    static class ApiClientConfiguration {

        @Bean
        @ConditionalOnMissingBean
        @ConditionalOnProperty("spring-plus.client.base-url")
        public ApiClient apiClient(ApiClientSettings settings,
                ObjectProvider<SslBundles> sslBundles,
                ObjectProvider<AuthProvider> authProvider,
                Environment environment) {
            ClientHttpRequestFactoryProvider factoryProvider =
                    new ClientHttpRequestFactoryProvider(sslBundles.getIfAvailable());
            factoryProvider.setEnvironment(environment);
            ApiClient.Builder builder = ApiClient.builder()
                    .properties(settings)
                    .clientHttpRequestFactoryProvider(factoryProvider);
            AuthProvider auth = authProvider.getIfAvailable();
            if (auth != null) {
                builder.authProvider(auth);
            }
            return builder.build();
        }

    }

    /**
     * ApiClient 调用指标（Micrometer 可选依赖）：classpath 存在 MeterRegistry 时
     * 为容器中所有 ApiClient Bean（含业务自建的）注入打点，指标名 {@code api.client.requests}。
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(MeterRegistry.class)
    static class ApiClientMetricsConfiguration {

        @Bean
        static ApiClientMetricsPostProcessor apiClientMetricsBinder(ObjectProvider<MeterRegistry> meterRegistry,
                ConfigurableListableBeanFactory beanFactory) {
            return new ApiClientMetricsPostProcessor(meterRegistry, beanFactory);
        }

    }

}
