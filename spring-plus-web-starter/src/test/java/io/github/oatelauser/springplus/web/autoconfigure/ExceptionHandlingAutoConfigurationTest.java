package io.github.oatelauser.springplus.web.autoconfigure;

import io.github.oatelauser.springplus.web.error.engine.DefaultExceptionLogger;
import io.github.oatelauser.springplus.web.error.engine.ExceptionLogger;
import io.github.oatelauser.springplus.web.error.engine.ExceptionOutputEngine;
import io.github.oatelauser.springplus.web.error.engine.HandlerExceptionAnnotationProcessor;
import io.github.oatelauser.springplus.web.error.engine.OutputProtocolResolver;
import io.github.oatelauser.springplus.web.error.mapper.DefaultExceptionMapper;
import io.github.oatelauser.springplus.web.error.mapper.ExceptionClassAnnotationMapper;
import io.github.oatelauser.springplus.web.error.mapper.ExceptionMapper;
import io.github.oatelauser.springplus.web.error.mapper.ExceptionMapperChain;
import io.github.oatelauser.springplus.web.error.mapper.ServerStatusMapper;
import io.github.oatelauser.springplus.web.error.output.ExceptionOutputProcessor;
import io.github.oatelauser.springplus.web.error.output.JsonExceptionProcessor;
import io.github.oatelauser.springplus.web.error.output.NdjsonExceptionProcessor;
import io.github.oatelauser.springplus.web.error.output.SseExceptionProcessor;
import io.github.oatelauser.springplus.web.error.sse.SseConnectionFactory;
import io.github.oatelauser.springplus.web.error.sse.SseExceptionEmitter;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockServletContext;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * {@link ExceptionHandlingAutoConfiguration} 守护测试（starter 装配语义）。
 * <p>
 * 关键前提：测试构建的容器<b>不做任何组件扫描</b>（只 register 配置类），完全模拟
 * 「消费方根包不在 {@code io.github.oatelauser} 下」的场景——这正是自动配置存在的意义：
 * error 包里的 {@code @Component} 在该场景下全部失联，只能靠本配置类装配。
 *
 * @author Oatelauser
 * @date 2026-08-24
 * @since 3.1
 */
class ExceptionHandlingAutoConfigurationTest {

    @Test
    void registersErrorHandlingBeansWithoutComponentScan() {
        try (AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext()) {
            context.setServletContext(new MockServletContext());
            context.register(ExceptionHandlingAutoConfiguration.class);
            context.refresh();

            // 引擎与两台启动期机器
            assertNotNull(context.getBean(OutputProtocolResolver.class), "协议探测应自动装配");
            assertNotNull(context.getBean(HandlerExceptionAnnotationProcessor.class), "注解规则扫描应自动装配");
            assertNotNull(context.getBean(ExceptionOutputEngine.class), "主引擎应自动装配");
            assertNotNull(context.getBean(GlobalExceptionAdvice.class), "@RestControllerAdvice 应经 @Import 装配");

            // Mapper 链：三个内置成员 + 链装配
            assertEquals(3, context.getBeansOfType(ExceptionMapper.class).size(),
                    "内置 Mapper 应恰好三个（类注解 / ServerStatus / 兜底）");
            assertNotNull(context.getBean(ServerStatusMapper.class));
            assertNotNull(context.getBean(ExceptionClassAnnotationMapper.class));
            assertNotNull(context.getBean(DefaultExceptionMapper.class));
            assertNotNull(context.getBean(ExceptionMapperChain.class));

            // 三协议渲染器
            assertEquals(3, context.getBeansOfType(ExceptionOutputProcessor.class).size(),
                    "内置渲染器应恰好三个（JSON / SSE / NDJSON）");
            assertNotNull(context.getBean(JsonExceptionProcessor.class));
            assertNotNull(context.getBean(SseExceptionProcessor.class));
            assertNotNull(context.getBean(NdjsonExceptionProcessor.class));

            // 日志器与 SSE B/C 档工具
            assertNotNull(context.getBean(ExceptionLogger.class), "默认日志器应自动装配");
            assertNotNull(context.getBean(SseExceptionEmitter.class));
            assertNotNull(context.getBean(SseConnectionFactory.class));
        }
    }

    @Test
    void customLoggerReplacesFrameworkDefault() {
        try (AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext()) {
            context.setServletContext(new MockServletContext());
            // 用户配置先注册（模拟业务 Bean 先于自动配置就位）
            context.register(CustomLoggerConfiguration.class, ExceptionHandlingAutoConfiguration.class);
            context.refresh();

            assertEquals(1, context.getBeansOfType(ExceptionLogger.class).size(),
                    "自定义 Logger 应整体替换默认实现，容器中只留一份");
            assertInstanceOf(CustomLoggerConfiguration.CapturingLogger.class,
                    context.getBean(ExceptionLogger.class));
        }
    }

    @Test
    void preRegisteredComponentMakesAutoConfigurationBackOff() {
        try (AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext()) {
            context.setServletContext(new MockServletContext());
            // 模拟「消费方自己扫描到 error 包」（如本仓库根包 io.github.oatelauser 的场景）：
            // 同类型 Bean 已由用户侧注册，自动配置的 @ConditionalOnMissingBean 必须退避，
            // 否则同类型两份会打穿引擎的单值注入。
            context.register(ScannedStyleConfiguration.class, ExceptionHandlingAutoConfiguration.class);
            context.refresh();

            assertEquals(1, context.getBeansOfType(OutputProtocolResolver.class).size(),
                    "扫描注册与自动配置不得并存出两份同类型 Bean");
            assertInstanceOf(MarkerResolver.class,
                    context.getBean(OutputProtocolResolver.class));
        }
    }

    /**
     * 业务自定义日志器：无操作实现（仅验证替换语义，不关心日志行为）。
     */
    @Configuration(proxyBeanMethods = false)
    static class CustomLoggerConfiguration {

        @Bean
        CapturingLogger capturingLogger() {
            return new CapturingLogger(new GlobalExceptionProperties());
        }

        static class CapturingLogger extends DefaultExceptionLogger {

            CapturingLogger(GlobalExceptionProperties properties) {
                super(properties);
            }
        }
    }

    /**
     * 模拟组件扫描先注册的 OutputProtocolResolver（标记子类，便于断言胜出者是用户侧实例）。
     */
    @Configuration(proxyBeanMethods = false)
    static class ScannedStyleConfiguration {

        @Bean
        OutputProtocolResolver outputProtocolResolver() {
            return new MarkerResolver();
        }
    }

    static class MarkerResolver extends OutputProtocolResolver {
    }

}
