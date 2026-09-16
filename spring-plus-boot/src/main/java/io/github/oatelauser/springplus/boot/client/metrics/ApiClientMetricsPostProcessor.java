package io.github.oatelauser.springplus.boot.client.metrics;

import io.github.oatelauser.springplus.boot.client.ApiClient;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;

/**
 * 为容器中每个 {@link ApiClient} Bean 注入 {@link MicrometerApiClientMetrics}（client tag = Bean 名）
 * <p>
 * 用 {@link SmartInitializingSingleton} 而不是 {@code BeanPostProcessor}：
 * 所有单例就绪后统一绑定，MeterRegistry 必然已创建——BPP 方案会在早期基础设施
 * Bean 的后置处理期触发 MeterRegistry 解析，与 Boot 指标自动装配形成循环依赖。
 * <p>
 * 仅在 classpath 存在 Micrometer 时由自动配置注册；lazy-init 的 ApiClient Bean 不在此刻存在，不打点。
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2026-08-28
 * @since 1.1
 */
public class ApiClientMetricsPostProcessor implements SmartInitializingSingleton {

    private final ObjectProvider<MeterRegistry> meterRegistry;
    private final ConfigurableListableBeanFactory beanFactory;

    public ApiClientMetricsPostProcessor(ObjectProvider<MeterRegistry> meterRegistry,
            ConfigurableListableBeanFactory beanFactory) {
        this.meterRegistry = meterRegistry;
        this.beanFactory = beanFactory;
    }

    @Override
    public void afterSingletonsInstantiated() throws BeansException {
        MeterRegistry registry = meterRegistry.getIfAvailable();
        if (registry == null) {
            return;
        }
        beanFactory.getBeansOfType(ApiClient.class)
                .forEach((beanName, client) ->
                        client.setMetrics(new MicrometerApiClientMetrics(beanName, registry)));
    }

}
