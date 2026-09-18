package io.github.oatelauser.springplus.web.autoconfigure;

import io.github.oatelauser.springplus.web.error.engine.DefaultExceptionLogger;
import io.github.oatelauser.springplus.web.error.engine.ExceptionLogger;
import io.github.oatelauser.springplus.web.error.engine.ExceptionOutputEngine;
import io.github.oatelauser.springplus.web.error.engine.HandlerExceptionAnnotationProcessor;
import io.github.oatelauser.springplus.web.error.engine.OutputProtocolResolver;
import io.github.oatelauser.springplus.web.error.advice.ModuleAdviceContractValidator;
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
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.web.servlet.DispatcherServlet;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

/**
 * 统一错误处理自动配置（starter 装配入口，设计文档 2.1 / 4.6）。
 * <p>
 * 本模块作为 starter 发布时，消费方的组件扫描通常覆盖不到
 * {@code io.github.oatelauser.springplus.web.error} 包——错误体系的 Bean 不能依赖
 * {@code @Component} 被扫到，必须在此集中声明（Spring Boot starter 语义，对齐
 * {@code ProblemDetailsWebMvcAutoConfiguration} 注册 {@code @ControllerAdvice} 的做法）。
 * 本类经 {@code META-INF/spring/...AutoConfiguration.imports} 登记，消费方引入 jar 即生效，
 * 与自己的根包名无关。
 *
 * <h3>与 {@code @Component} 注解并存的双通道</h3>
 * <p>
 * error 包的类保留 {@code @Component} / {@code @RestControllerAdvice} 注解：
 * <ul>
 *   <li>扫得到本包的应用（如本仓库 apartment-application，根包 {@code io.github.oatelauser}）——
 *       组件扫描先注册，本类所有 Bean 因 {@link ConditionalOnMissingBean} 整体退避，
 *       不会重复注册（同类型两份会打穿 {@code List} 集合注入与单值注入的语义）；</li>
 *   <li>扫不到本包的外部应用——由本类兜底提供全部 Bean。</li>
 * </ul>
 * 两条通道互斥于条件求值，殊途同归。{@link GlobalExceptionAdvice} 走 {@link Import}：
 * Spring 对「已扫描类再被 @Import」按类去重，天然无双注册问题。
 *
 * <h3>可覆盖性</h3>
 * <p>
 * 每个 Bean 都带 {@link ConditionalOnMissingBean}：业务方注册<b>同类型</b> Bean 即可替换框架
 * 默认实现（如自定义 {@code ExceptionLogger}，设计文档 10.3）；{@code ExceptionMapper} 与
 * {@code ExceptionOutputProcessor} 注入的是集合，业务新增实现天然共存而非替换。
 *
 * @author Oatelauser
 * @date 2026-08-24
 * @since 3.1
 */
@AutoConfiguration
@Import(GlobalExceptionAdvice.class)
@ConditionalOnClass(DispatcherServlet.class)
@EnableConfigurationProperties(GlobalExceptionProperties.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class ExceptionHandlingAutoConfiguration {

    /**
     * 协议探测：启动期把每个 HandlerMethod 的协议预解析进查表，运行期 O(1)（设计文档 7.1）。
     */
    @Bean
    @ConditionalOnMissingBean
    public OutputProtocolResolver outputProtocolResolver() {
        return new OutputProtocolResolver();
    }

    /**
     * 方法 / 类级 {@code @ExceptionResponse} 家族注解的规则扫描缓存（设计文档 7.1）。
     * <p>
     * {@code protocols} 直设的启动期 {@link IllegalStateException} 快速失败（Q14a）在扫描逻辑内。
     */
    @Bean
    @ConditionalOnMissingBean
    public HandlerExceptionAnnotationProcessor handlerExceptionAnnotationProcessor(ApplicationContext applicationContext) {
        return new HandlerExceptionAnnotationProcessor(applicationContext);
    }

    /**
     * Mapper 链成员一：{@code ServiceException} 等 {@code ServerStatus} 体系异常的翻译（order = -900）。
     */
    @Bean
    @ConditionalOnMissingBean
    public ServerStatusMapper serverStatusMapper() {
        return new ServerStatusMapper();
    }

    /**
     * Mapper 链成员二：异常类上 {@code @ExceptionResponse} 家族注解的懒扫描翻译（order = -1000）。
     */
    @Bean
    @ConditionalOnMissingBean
    public ExceptionClassAnnotationMapper exceptionClassAnnotationMapper(ApplicationContext applicationContext) {
        return new ExceptionClassAnnotationMapper(applicationContext);
    }

    /**
     * Mapper 链兜底：未知异常按 {@link GlobalExceptionProperties} 的 code / msg / showError 翻译（order = MAX）。
     */
    @Bean
    @ConditionalOnMissingBean
    public DefaultExceptionMapper defaultExceptionMapper(GlobalExceptionProperties properties) {
        return new DefaultExceptionMapper(properties);
    }

    /**
     * Mapper 链装配：收集容器中全部 {@link ExceptionMapper}（内置 + 业务自定义），按 order 升序固化。
     */
    @Bean
    @ConditionalOnMissingBean
    public ExceptionMapperChain exceptionMapperChain(List<ExceptionMapper> mappers) {
        return new ExceptionMapperChain(mappers);
    }

    /**
     * HTTP_JSON 协议渲染器。
     */
    @Bean
    @ConditionalOnMissingBean
    public JsonExceptionProcessor jsonExceptionProcessor() {
        return new JsonExceptionProcessor();
    }

    /**
     * HTTP_SSE 协议渲染器（默认事件名来自 {@link GlobalExceptionProperties}）。
     */
    @Bean
    @ConditionalOnMissingBean
    public SseExceptionProcessor sseExceptionProcessor(GlobalExceptionProperties properties) {
        return new SseExceptionProcessor(properties);
    }

    /**
     * NDJSON 协议渲染器（两态渲染 + 已提交补写）。
     * <p>
     * {@code JsonMapper} 容器有则用之（与数据行序列化同源），无则兜底
     * {@code JsonUtils.shared()} 全局实例——starter 场景消费方不一定注册 JsonMapper Bean。
     */
    @Bean
    @ConditionalOnMissingBean
    public NdjsonExceptionProcessor ndjsonExceptionProcessor(ObjectProvider<JsonMapper> jsonMapper) {
        return new NdjsonExceptionProcessor(jsonMapper.getIfAvailable());
    }

    /**
     * 默认异常日志器（线性四步日志决策链，设计文档 5.3）。
     * <p>
     * 条件按接口类型 {@link ExceptionLogger} 判断：业务方注册任意自定义实现即整体替换默认实现。
     */
    @Bean
    @ConditionalOnMissingBean(ExceptionLogger.class)
    public DefaultExceptionLogger defaultExceptionLogger(GlobalExceptionProperties properties) {
        return new DefaultExceptionLogger(properties);
    }

    /**
     * 异常渲染主引擎：分发 / 协议探测 / P0–P3 描述解析 / 日志 / 已提交能力位派发（设计文档 5.1）。
     */
    @Bean
    @ConditionalOnMissingBean
    public ExceptionOutputEngine exceptionOutputEngine(OutputProtocolResolver protocolResolver,
            HandlerExceptionAnnotationProcessor ruleScanner, ExceptionMapperChain mapperChain,
            ExceptionLogger logger, List<ExceptionOutputProcessor> processors) {
        return new ExceptionOutputEngine(protocolResolver, ruleScanner, mapperChain, logger, processors);
    }

    /**
     * SSE 流内异常工具（B 档，设计文档 8.2）：把异常转 {@code app-error} 事件写到已有 emitter。
     */
    @Bean
    @ConditionalOnMissingBean
    public SseExceptionEmitter sseExceptionEmitter(ExceptionOutputEngine outputEngine,
            SseExceptionProcessor exceptionProcessor) {
        return new SseExceptionEmitter(outputEngine, exceptionProcessor);
    }

    /**
     * SSE 连接工厂（C 档，设计文档 8.3）：executor 由业务显式传入，框架不隐式注入默认池。
     */
    @Bean
    @ConditionalOnMissingBean
    public SseConnectionFactory sseConnectionFactory(SseExceptionEmitter exceptionEmitter) {
        return new SseConnectionFactory(exceptionEmitter);
    }

    /**
     * 模块级 advice 契约启动期校验（规则一/二均告警，见该类 javadoc）。
     */
    @Bean
    @ConditionalOnMissingBean
    public ModuleAdviceContractValidator moduleAdviceContractValidator(ConfigurableListableBeanFactory beanFactory) {
        return new ModuleAdviceContractValidator(beanFactory);
    }

}
