package io.github.oatelauser.springplus.web.error.mapper;

import io.github.oatelauser.springplus.web.error.annotation.ExceptionResponse;
import io.github.oatelauser.springplus.web.error.descriptor.ErrorDescriptor;
import io.github.oatelauser.springplus.web.error.engine.AnnotationToTemplateConverter;
import io.github.oatelauser.springplus.web.error.engine.ErrorDescriptorTemplate;
import io.github.oatelauser.springplus.web.error.engine.ExceptionAnnotationUtils;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内置 Mapper：解析异常类上的 {@link ExceptionResponse} 家族注解（v3.0，设计文档 6.4）。
 * <p>
 * 这是 v1.0「异常类上的 {@code @ExceptionResponse} 也参与匹配」（旧优先级 200）这一行为的
 * <b>新承载方式</b>——从旧的 {@code ExceptionResponseProcessor} 里拆出来，作为一个标准
 * {@link ExceptionMapper} 实现。好处（设计 6.3）：
 * <ul>
 *   <li><b>扩展模型统一</b>：异常类注解、第三方 SDK 异常都走同一条 Mapper 链。</li>
 *   <li><b>优先级清晰</b>：{@link #order()} = -1000，比一般业务 Mapper（正数）优先，但仍在
 *       Mapper 链路径下（P2），让方法/类级注解（P0）、handler defaultDescriptor（P1）能覆盖它。</li>
 *   <li><b>业务可关闭</b>：不想要「异常类注解」这种行为时，排除这个 Bean 即可。</li>
 * </ul>
 *
 * <h3>懒加载 + 缓存</h3>
 * <p>
 * 不在启动期扫描全部异常类（无法穷举），而是<b>懒加载</b>：首次处理某异常类时才扫描其注解，
 * 结果缓存进 {@link #cache}（{@link ConcurrentHashMap}）。后续命中只做一次 {@code Map.get} +
 * {@link ErrorDescriptorTemplate#materialize(Throwable)}，零反射（设计 1.2）。
 *
 * <h3>v3.0 改造：三类硬编码扫描 → 家族发现器（修复 NDJSON 漏扫）</h3>
 * <p>
 * v2.x 在此逐类硬编码扫描（普通 / Json / Sse 三段），v2.2 新增 NDJSON 时<b>漏改了这里</b>——
 * 异常类上贴 {@code @NdjsonExceptionResponse} 静默失效，正是「扫描方必须枚举家族成员」这一
 * 结构性缺陷的实证（Q7a）。v3.0 改用 {@link ExceptionAnnotationUtils#findFamilyAnnotations(Class)}
 * 家族反查（含 {@code @Repeatable} 容器解包、父类链、接口），任意派生注解（含业务自定义）
 * 零改动接入；v2 为规避 Spring 合并视图丢字段而写的 {@code protocols().length > 0} 过滤 hack
 * 一并消失——finder 返回<b>原始注解实例</b>，派生注解的协议专属属性（event/retry 等）天然保留，
 * 由 {@link AnnotationToTemplateConverter} 统一转换。
 *
 * <h3>典型用法</h3>
 * <pre>{@code
 * @ExceptionResponse(code = "B0204", msg = "用户名已存在: {exception}")
 * public class UsernameDuplicatedException extends RuntimeException { ... }
 *
 * // Controller 无需重复贴注解，抛 UsernameDuplicatedException 自动命中 B0204
 * }</pre>
 *
 * @author Oatelauser
 * @date 2026-06-15
 * @since 2.0
 */
public class ExceptionClassAnnotationMapper implements ExceptionMapper {

    /**
     * 异常类 → 该类上所有注解编译出的 Template 列表。
     * <p>
     * 空 list 表示「扫描过但无注解」（等价于 NONE 哨兵语义），避免反复扫描。
     */
    private final Map<Class<?>, List<ErrorDescriptorTemplate>> cache = new ConcurrentHashMap<>();

    private final ApplicationContext applicationContext;

    public ExceptionClassAnnotationMapper(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    /**
     * order = -1000：在所有业务 Mapper（正数）之前被遍历，保证「异常类注解」这一框架级行为优先；
     * 但仍小于 {@code ServerStatusMapper}(-900) 之外的业务 Mapper，可被业务覆盖。
     */
    @Override
    public int order() {
        return -1000;
    }

    @Nullable
    @Override
    public ErrorDescriptor map(Throwable ex, ExceptionMapperContext ctx) {
        // 懒加载：首次见到该异常类时扫描注解，之后纯查表。
        List<ErrorDescriptorTemplate> templates = this.cache.computeIfAbsent(ex.getClass(), this::resolveTemplates);
        for (ErrorDescriptorTemplate template : templates) {
            // 协议过滤：只在该协议下命中。同异常类可能有多条注解（如一条 JSON 一条 SSE）。
            if (template.matchesProtocol(ctx.protocol())) {
                return template.materialize(ex);
            }
        }
        return null;
    }

    /**
     * 家族发现器扫描异常类（含父类链 / 接口 / 容器解包）上的全部家族注解，逐条编译成 Template。
     * <p>
     * 原始注解实例直传 {@link AnnotationToTemplateConverter#convert}——通用字段经合并视图取
     * {@code @AliasFor} 桥接值，协议专属字段由 hint 工厂二次合并读取，无需调用方分类。
     *
     * @param exceptionClass 异常类
     * @return Template 列表（无注解时为空 list，{@link List#copyOf} 不可变）
     */
    private List<ErrorDescriptorTemplate> resolveTemplates(Class<?> exceptionClass) {
        List<ErrorDescriptorTemplate> result = new ArrayList<>();
        for (Annotation annotation : ExceptionAnnotationUtils.findFamilyAnnotations(exceptionClass)) {
            result.add(AnnotationToTemplateConverter.convert(annotation, this.applicationContext));
        }
        return List.copyOf(result);
    }

}
