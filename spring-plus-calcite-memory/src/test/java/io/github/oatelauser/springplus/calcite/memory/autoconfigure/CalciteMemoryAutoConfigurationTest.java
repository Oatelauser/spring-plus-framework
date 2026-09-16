package io.github.oatelauser.springplus.calcite.memory.autoconfigure;

import io.github.oatelauser.springplus.calcite.memory.engine.MemoryQueryEngine;
import io.github.oatelauser.springplus.calcite.memory.engine.MemoryQuerySession;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.RequestScope;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 5.4 验收：Spring Boot 自动配置与 Micrometer 指标桥接。
 *
 * <p>覆盖：类条件禁用、属性禁用、请求级 Session 销毁回调、Micrometer 指标可见性。</p>
 */
class CalciteMemoryAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CalciteMemoryAutoConfiguration.class));

    /** 1) 核心类被屏蔽时，整条自动配置不生效，引擎 bean 缺席。 */
    @Test
    void engineAbsentWhenCoreClassFiltered() {
        runner.withClassLoader(new FilteredClassLoader(MemoryQueryEngine.class))
                .run(ctx -> assertThat(ctx).doesNotHaveBean(MemoryQueryEngine.class));
    }

    /** 2) calcite.memory.enabled=false 时引擎 bean 缺席。 */
    @Test
    void engineAbsentWhenDisabledByProperty() {
        runner.withPropertyValues("calcite.memory.enabled=false")
                .run(ctx -> assertThat(ctx).doesNotHaveBean(MemoryQueryEngine.class));
    }

    /** 3) 请求结束时，请求级 Session 应被自动关闭（destroy 回调 → close()）。 */
    @Test
    void sessionClosedAtRequestEnd() {
        runner.withUserConfiguration(RequestScopeRegistrar.class)
                .run(ctx -> {
                    ServletRequestAttributes attrs =
                            new ServletRequestAttributes(new MockHttpServletRequest());
                    RequestContextHolder.setRequestAttributes(attrs);
                    try {
                        MemoryQuerySession session = ctx.getBean(MemoryQuerySession.class);
                        assertThat(session.isClosed()).isFalse();
                        // 触发请求销毁回调：Spring 执行 @Bean 推断的 close() 销毁方法
                        attrs.requestCompleted();
                        assertThat(session.isClosed()).isTrue();
                    } finally {
                        RequestContextHolder.resetRequestAttributes();
                    }
                });
    }

    /** 4) MeterRegistry 存在时，查询与注册事件应产生对应 Micrometer 指标。 */
    @Test
    void micrometerMetersRegisteredAfterQuery() {
        runner.withBean(MeterRegistry.class, SimpleMeterRegistry::new)
                .run(ctx -> {
                    MeterRegistry meterRegistry = ctx.getBean(MeterRegistry.class);
                    MemoryQueryEngine engine = ctx.getBean(MemoryQueryEngine.class);
                    try (MemoryQuerySession session = engine.openSession()) {
                        session.register("orders", List.of(new OrderRow(1, "PAID")), OrderRow.class);
                        session.query("SELECT id FROM orders");
                    }
                    assertThat(meterRegistry.find("calcite.memory.query.duration").timer()).isNotNull();
                    assertThat(meterRegistry.find("calcite.memory.query.rows").counter()).isNotNull();
                    assertThat(meterRegistry.find("calcite.memory.table.registered").counter()).isNotNull();
                });
    }

    /** 非 web 容器手动注册 request scope，使请求级 Session bean 可在测试中创建与销毁。 */
    static class RequestScopeRegistrar {
        @Bean
        static BeanFactoryPostProcessor requestScopeRegistrar() {
            return beanFactory -> ((ConfigurableListableBeanFactory) beanFactory)
                    .registerScope(WebApplicationContext.SCOPE_REQUEST, new RequestScope());
        }
    }

    /** 注册用最小 POJO：final 字段（与既有测试约定一致），Integer 包装类型。 */
    static final class OrderRow {
        final Integer id;
        final String status;

        OrderRow(Integer id, String status) {
            this.id = id;
            this.status = status;
        }
    }
}
