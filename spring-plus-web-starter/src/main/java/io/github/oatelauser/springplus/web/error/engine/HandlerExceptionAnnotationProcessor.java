package io.github.oatelauser.springplus.web.error.engine;

import io.github.oatelauser.springplus.web.error.annotation.ExceptionResponse;
import io.github.oatelauser.springplus.web.error.descriptor.ErrorDescriptor;
import io.github.oatelauser.springplus.web.error.descriptor.OutputProtocol;
import io.github.oatelauser.springplus.web.process.HandlerMethodProcessor;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationContext;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 方法 / 类级 {@link ExceptionResponse} 家族注解的规则扫描缓存（v3.0，设计文档 7.1）。
 * <p>
 * 启动期扫描每个 {@link HandlerMethod} 的<b>方法级</b>（优先级 100）与<b>控制器类级</b>（优先级 300）
 * 注解，经 {@link AnnotationToTemplateConverter} 编译成 {@link MethodExceptionRule} 列表并按
 * 优先级排序缓存；运行期 {@link #resolve} 做异常类型 + 协议匹配，产出 {@link ErrorDescriptor}。
 * <b>注意</b>（设计 7.1）：「异常类级注解（200）」由 {@code ExceptionClassAnnotationMapper} 懒扫描，
 * 不在本表——本类只负责方法 / 控制器类两层。
 *
 * <h3>v3.0 改造：家族泛化扫描（Q7a）</h3>
 * <p>
 * v2.x 有四套按注解类型硬编码的收集方法（addPlainRules / addJsonRules / addSseRules / addNdjsonRules），
 * 且普通注解扫描要靠 {@code protocols.length > 0} 技巧过滤 Spring 合并视图——v2.2 给 NDJSON
 * 补扫描时漏改异常类映射层，就是这套手工枚举的直接恶果。
 * v3.0 改为 {@link ExceptionAnnotationUtils} 反查家族：一个收集循环通吃父注解 + 全部派生注解
 * （含业务自定义派生，Q15a），新协议派生注解<b>零改动</b>进入本扫描。
 *
 * <h3>v3.0 改造：protocols 直设启动失败（Q14a）</h3>
 * <p>
 * {@code @ExceptionResponse(protocols = ...)} 是派生注解专用字段，业务直设在 v2.x 会被静默跳过
 * （哑巴注解）。v3.0 扫描到直设实例立即抛 {@link IllegalStateException} 让启动失败，
 * 把配置错误拦在最前面。
 *
 * <h3>匹配算法（移植自 v2.0）</h3>
 * <ol>
 *   <li>协议过滤：只保留 {@code template.matchesProtocol(protocol)} 的规则。</li>
 *   <li>精确异常类型匹配：选 {@code (effectiveOrder, level)} 最小者。</li>
 *   <li>父类匹配：选 {@code (继承距离, effectiveOrder, level)} 最小者。</li>
 * </ol>
 * {@code effectiveOrder = order != 0 ? order : level}（用户显式 order 优先于层级默认值）。
 *
 * @author Oatelauser
 * @date 2026-06-15
 * @since 2.0
 */
public class HandlerExceptionAnnotationProcessor implements HandlerMethodProcessor {

    /**
     * 方法级注解优先级。
     */
    private static final int LEVEL_METHOD = 100;
    /**
     * 控制器类级注解优先级。
     */
    private static final int LEVEL_CONTROLLER_CLASS = 300;

    private final Map<Method, List<MethodExceptionRule>> cache = new ConcurrentHashMap<>();

    private final ApplicationContext applicationContext;

    public HandlerExceptionAnnotationProcessor(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    public boolean supports(Set<String> urls, HandlerMethod handlerMethod) {
        return true;
    }

    @Override
    public void handleMethod(Set<String> urls, RequestMappingInfo requestMappingInfo, HandlerMethod handlerMethod) {
        this.processHandlerMethod(handlerMethod);
    }

    /**
     * 启动期扫描方法 + 类注解，编译成排序后的规则列表缓存。
     */
    private void processHandlerMethod(HandlerMethod handlerMethod) {
        Method method = handlerMethod.getMethod();
        List<MethodExceptionRule> rules = new ArrayList<>();

        // 1. 方法级注解（优先级最高）；2. 控制器类级注解（优先级最低）。
        this.collect(rules, LEVEL_METHOD, ExceptionAnnotationUtils.findFamilyAnnotations(method));
        this.collect(rules, LEVEL_CONTROLLER_CLASS, ExceptionAnnotationUtils.findFamilyAnnotations(handlerMethod.getBeanType()));

        if (rules.isEmpty()) {
            return;
        }
        // 排序：effectiveOrder 升序，同 order 时 level 升序（方法级 100 < 类级 300）。
        rules.sort(Comparator.comparingInt((MethodExceptionRule r) -> r.effectiveOrder)
                .thenComparingInt(r -> r.level));
        this.cache.put(method, List.copyOf(rules));
    }

    /**
     * 运行期解析：异常类型 + 协议匹配，产出 {@link ErrorDescriptor}。
     *
     * @return 命中的描述；无匹配返回 null（让引擎走 P1/P2）
     */
    @Nullable
    @SuppressWarnings("all")
    public ErrorDescriptor resolve(Throwable exception, @Nullable HandlerMethod handlerMethod, OutputProtocol protocol) {
        if (handlerMethod == null) {
            return null;
        }
        List<MethodExceptionRule> rules = this.cache.get(handlerMethod.getMethod());
        if (rules == null || rules.isEmpty()) {
            return null;
        }

        // 第一轮：协议过滤
        Class<?> exceptionClass = exception.getClass();
        List<MethodExceptionRule> candidates = new ArrayList<>();
        for (MethodExceptionRule rule : rules) {
            if (rule.template.matchesProtocol(protocol)) {
                candidates.add(rule);
            }
        }
        if (candidates.isEmpty()) {
            return null;
        }

        // 第二轮：精确异常类型匹配，选 (effectiveOrder, level) 最小者
        MethodExceptionRule exact = null;
        for (MethodExceptionRule rule : candidates) {
            for (Class<? extends Throwable> exceptionType : rule.template.exceptionTypes) {
                if (exceptionType.equals(exceptionClass)) {
                    if (exact == null || compareKey(rule, exact) < 0) {
                        exact = rule;
                    }
                    break;
                }
            }
        }
        if (exact != null) {
            return exact.template.materialize(exception);
        }

        // 第三轮：父类匹配，选 (继承距离, effectiveOrder, level) 最小者。
        MethodExceptionRule best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (MethodExceptionRule rule : candidates) {
            for (Class<? extends Throwable> exceptionType : rule.template.exceptionTypes) {
                if (exceptionType.isAssignableFrom(exceptionClass)) {
                    int distance = inheritanceDistance(exceptionClass, exceptionType);
                    if (distance < bestDistance || (distance == bestDistance &&
                            best != null && compareKey(rule, best) < 0)) {
                        bestDistance = distance;
                        best = rule;
                    }
                }
            }
        }
        return best == null ? null : best.template.materialize(exception);
    }

    // ─────────────────────────────────────────────────────────────
    // 规则构建
    // ─────────────────────────────────────────────────────────────

    /**
     * 把一批家族注解（原始实例）展开成规则：每条注解一次 convert，每个声明的异常类型一条规则，
     * 共享同一模板。
     *
     * @throws IllegalStateException 业务在 {@code @ExceptionResponse} 上直设 protocols（Q14a 启动失败）
     */
    private void collect(List<MethodExceptionRule> sink, int level, List<Annotation> annotations) {
        for (Annotation annotation : annotations) {
            if (annotation instanceof ExceptionResponse plain && plain.protocols().length > 0) {
                throw new IllegalStateException("业务方不得直接设置 @ExceptionResponse.protocols（"
                        + "该字段为派生注解专用，用于锁死协议归属）。需要协议过滤请改用 "
                        + "@JsonExceptionResponse / @SseExceptionResponse / @NdjsonExceptionResponse；"
                        + "需要全协议通用请移除 protocols 设置。出错注解 protocols="
                        + List.of(plain.protocols()));
            }
            ErrorDescriptorTemplate template = AnnotationToTemplateConverter.convert(annotation, this.applicationContext);
            for (Class<? extends Throwable> exceptionType : template.exceptionTypes) {
                sink.add(new MethodExceptionRule(template, exceptionType, level));
            }
        }
    }

    /**
     * 比较 (effectiveOrder, level)，小者优先。
     */
    private static int compareKey(MethodExceptionRule a, MethodExceptionRule b) {
        int cmp = Integer.compare(a.effectiveOrder, b.effectiveOrder);
        return cmp != 0 ? cmp : Integer.compare(a.level, b.level);
    }

    /**
     * 计算继承距离（exClass 相对 target），0 表示同类。
     */
    private static int inheritanceDistance(Class<?> exceptionClass, Class<?> targetClass) {
        int distance = 0;
        Class<?> current = exceptionClass;
        while (current != null && !current.equals(targetClass)) {
            current = current.getSuperclass();
            distance++;
        }
        return current != null ? distance : Integer.MAX_VALUE;
    }

    /**
     * 一条匹配规则：模板（自带全部注解固化信息）+ 命中的具体异常类型 + 层级。
     * <p>
     * v3.0 瘦身：exceptionTypes / order 收进模板，规则只补「层级」这个扫描上下文维度。
     */
    private static final class MethodExceptionRule {
        final ErrorDescriptorTemplate template;
        final Class<? extends Throwable> matchedType;
        final int level;
        /**
         * 有效优先级：注解显式 order（非 0）优先，否则用层级（方法 100 / 类 300）。
         */
        final int effectiveOrder;

        MethodExceptionRule(ErrorDescriptorTemplate template, Class<? extends Throwable> matchedType, int level) {
            this.template = template;
            this.matchedType = matchedType;
            this.level = level;
            this.effectiveOrder = template.order != 0 ? template.order : level;
        }
    }
}
