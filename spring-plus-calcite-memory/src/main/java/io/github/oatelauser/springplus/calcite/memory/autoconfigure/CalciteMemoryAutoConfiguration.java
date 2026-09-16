package io.github.oatelauser.springplus.calcite.memory.autoconfigure;

import io.github.oatelauser.springplus.calcite.memory.engine.MemoryQueryEngine;
import io.github.oatelauser.springplus.calcite.memory.engine.SessionConfig;
import io.github.oatelauser.springplus.calcite.memory.metrics.MemoryMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * 内存表查询框架 Spring Boot 自动配置。
 *
 * <p>激活条件：类路径存在 {@link MemoryQueryEngine} 且 {@code calcite.memory.enabled} 非 {@code false}（默认启用）。</p>
 *
 * <p>装配内容：</p>
 * <ul>
 *   <li>{@link SessionConfig} — 由 {@link CalciteMemoryProperties} 映射</li>
 *   <li>{@link MemoryQueryEngine} — 持有应用级 schema 与指标 SPI</li>
 *   <li>{@link MemoryMetrics} — {@link MeterRegistry} 可用时装配 Micrometer 实现，否则回退 {@link MemoryMetrics#NOOP}</li>
 *   <li>{@link SessionLifecycleBinder} — Web 环境下提供请求级 Session bean</li>
 * </ul>
 *
 * <p>两个 {@link MemoryMetrics} 候选均带 {@link ConditionalOnMissingBean}：声明在前者优先。
 * Micrometer 缺席时 {@code micrometerMemoryMetrics} 因 {@link ConditionalOnClass} 不生效，
 * {@code calciteNoopMemoryMetrics} 自然兜底。</p>
 */
@AutoConfiguration
@ConditionalOnClass(MemoryQueryEngine.class)
@ConditionalOnProperty(prefix = "calcite.memory", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(CalciteMemoryProperties.class)
@Import(SessionLifecycleBinder.class)
public class CalciteMemoryAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public SessionConfig calciteSessionConfig(CalciteMemoryProperties properties) {
        return properties.toSessionConfig();
    }

    @Bean
    @ConditionalOnMissingBean
    public MemoryQueryEngine calciteMemoryQueryEngine(SessionConfig sessionConfig,
            ObjectProvider<MemoryMetrics> metricsProvider) {
        return MemoryQueryEngine.create(sessionConfig, metricsProvider.getIfAvailable(MemoryMetrics::noop));
    }

    @Bean
    @ConditionalOnMissingBean(MemoryMetrics.class)
    @ConditionalOnClass(MeterRegistry.class)
    public MemoryMetrics micrometerMemoryMetrics(ObjectProvider<MeterRegistry> registryProvider) {
        MeterRegistry registry = registryProvider.getIfAvailable();
        return registry != null ? new MicrometerMemoryMetrics(registry) : MemoryMetrics.NOOP;
    }

    @Bean
    @ConditionalOnMissingBean(MemoryMetrics.class)
    public MemoryMetrics calciteNoopMemoryMetrics() {
        return MemoryMetrics.NOOP;
    }
}
