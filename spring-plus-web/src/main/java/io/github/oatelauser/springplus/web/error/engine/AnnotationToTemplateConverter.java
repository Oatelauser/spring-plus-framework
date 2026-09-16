package io.github.oatelauser.springplus.web.error.engine;

import io.github.oatelauser.springplus.web.error.annotation.ExceptionResponse;
import io.github.oatelauser.springplus.web.error.annotation.SseExceptionResponse;
import io.github.oatelauser.springplus.web.error.descriptor.OutputProtocol;
import io.github.oatelauser.springplus.web.error.output.ErrorHint;
import io.github.oatelauser.springplus.web.error.output.ExceptionBodyCustomizer;
import io.github.oatelauser.springplus.web.error.output.SseErrorHint;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * <b>框架内部工具，业务方请勿直接使用</b>。把任意 {@link ExceptionResponse} 家族注解
 * 转换为 {@link ErrorDescriptorTemplate}（v3.0）。
 * <p>
 * 这是「启动期预计算」的核心执行者：在容器启动 / 首次扫描时被调用，把注解属性 +
 * bodyCustomizer Bean 实例 + 协议 hint 固化进 Template，之后运行期再也不碰注解、不 getBean、不反射
 * （设计 1.2 运行期零反射）。
 *
 * <h3>v3.0 改造：内置 hint 工厂表替代 @ErrorHintBy / Extractor 体系（Q8b）</h3>
 * <p>
 * v2.x 为「注解 → 协议元数据」专门造了一组 SPI（{@code @ErrorHintBy} 元注解 +
 * {@code ErrorHintExtractor} 接口 + 三个 extractor 实现 + converter 里的反射 newInstance 缓存）。
 * 复盘结论：这个扩展点<b>从未被业务用过，也不可能被用</b>——hint 的种类由 sealed 家族 + 各协议
 * 处理器编译期锁定，第三方 extractor 造不出新 hint 类型；而框架内置的 extractor 又只是把注解
 * 属性抄进 record。四两拨千斤的反例：为了三层间接，付出 6 个类 + 1 处反射缓存。
 * <p>
 * v3.0 蒸发整套体系，改为一张内置工厂表 {@link #HINT_FACTORIES}：协议 → 工厂函数。
 * 派生注解的协议专属属性（event/retry）由对应工厂<b>二次合并读取</b>派生注解类型——
 * 业务自定义派生注解（经 {@code @AliasFor} 桥接，Q15a）同样被正确解析。
 * 加新协议时的改动面收敛为：新注解 + 新 hint record（入 sealed 家族）+ 工厂表加一行 + 新处理器，
 * converter 的通用骨架不变。
 *
 * <h3>bodyCustomizer 的 fail-fast</h3>
 * <p>
 * 注解声明的 {@code output} 若非哨兵（{@link ExceptionBodyCustomizer}.class），转换器会立即
 * {@code getBean} 取实例；取不到（没注册成 Bean）直接抛异常，让应用启动失败——避免运行期才发现
 * 定制器缺失导致 NPE。
 *
 * @author Oatelauser
 * @date 2026-06-15
 * @since 2.0
 */
public final class AnnotationToTemplateConverter {

    /**
     * 内置 hint 工厂表：协议 → 「派生注解实例 → 协议专属渲染参数」。
     * <p>
     * JSON / NDJSON 无协议专属渲染参数（状态码已升维 statusIntent），不在表内；
     * SSE 的 event/retry 是事件帧真实字段，由 {@link #sseHint} 二次合并读取。
     * 新协议有专属参数时在此加一行 + hint record 入 sealed 家族。
     */
    private static final Map<OutputProtocol, Function<Annotation, ErrorHint>> HINT_FACTORIES = Map.of(
            OutputProtocol.HTTP_SSE, AnnotationToTemplateConverter::sseHint);

    private AnnotationToTemplateConverter() {
    }

    /**
     * 把任意 {@link ExceptionResponse} 家族注解转换为 Template。
     *
     * @param annotation         家族注解原始实例（父注解 / 任意派生注解，含业务自定义派生）
     * @param applicationContext Spring 容器，用于 getBean bodyCustomizer
     * @return 完整的 Template（注解的固化镜像：异常类型、order、code、消息模板、协议、状态码、定制器、hint）
     */
    public static ErrorDescriptorTemplate convert(Annotation annotation,
            ApplicationContext applicationContext) {
        // 合并视图：@AliasFor 桥接后的通用字段值（普通注解即自身）。
        ExceptionResponse merged = ExceptionAnnotationUtils.mergedView(annotation);

        ErrorDescriptorTemplate t = new ErrorDescriptorTemplate();
        t.exceptionTypes = merged.value();
        t.order = merged.order();
        t.code = merged.code();
        t.messageTemplate = merged.msg();
        t.showException = merged.showException();
        t.hasPlaceholder = hasPlaceholder(t.messageTemplate);
        t.protocols = toProtocolSet(merged.protocols());
        // OK 归一化为 null：模板里 null = 未指定，与「显式要求 200」区分。
        t.statusIntent = merged.httpStatus() != HttpStatus.OK ? merged.httpStatus() : null;
        t.logPolicy = merged.logPolicy();
        t.bodyCustomizer = resolveBodyCustomizer(merged.output(), applicationContext);
        t.errorHint = extractHint(annotation, merged.protocols());
        return t;
    }

    /**
     * 按锁死协议查工厂表取协议专属渲染参数。
     * <p>
     * 普通注解（protocols 空，全协议通用）无协议专属参数；锁死协议但不在工厂表内
     * （JSON / NDJSON）同样为 null——处理器用各协议缺省兜底。
     */
    @Nullable
    private static ErrorHint extractHint(Annotation annotation, OutputProtocol[] protocols) {
        if (protocols.length != 1) {
            return null;
        }
        Function<Annotation, ErrorHint> factory = HINT_FACTORIES.get(protocols[0]);
        return factory != null ? factory.apply(annotation) : null;
    }

    /**
     * SSE hint 工厂：二次合并读取 {@link SseExceptionResponse} 视图取 event / retry。
     * <p>
     * 用合并视图而非直接强转，业务自定义派生注解（{@code @MySseExceptionResponse} 经
     * {@code @AliasFor} 桥接 event/retry，Q15a）同样正确解析；event 空串归一化为 null
     * （处理器回落全局缺省事件名）。
     */
    @Nullable
    private static ErrorHint sseHint(Annotation annotation) {
        SseExceptionResponse sse = AnnotatedElementUtils.getMergedAnnotation(
                ExceptionAnnotationUtils.asElement(annotation), SseExceptionResponse.class);
        if (sse == null) {
            // 防御：协议判定为 HTTP_SSE 但注解与 @SseExceptionResponse 无桥接关系（自定义派生未挂元注解）。
            return null;
        }
        String event = StringUtils.hasText(sse.event()) ? sse.event() : null;
        return new SseErrorHint(event, sse.retry());
    }

    /**
     * 解析 bodyCustomizer：哨兵值（{@link ExceptionBodyCustomizer}.class）返回 null（表示走默认响应体），
     * 否则从容器取 Bean 实例。取不到则 fail-fast 抛出，避免运行期 NPE。
     */
    @Nullable
    private static ExceptionBodyCustomizer resolveBodyCustomizer(
            Class<? extends ExceptionBodyCustomizer> customizerClass,
            ApplicationContext applicationContext) {
        if (customizerClass == ExceptionBodyCustomizer.class) {
            return null;
        }
        try {
            return applicationContext.getBean(customizerClass);
        } catch (NoSuchBeanDefinitionException e) {
            throw new IllegalStateException("注解声明的 ExceptionBodyCustomizer 未在容器中注册: "
                    + customizerClass.getName() + "，请确保它标注了 @Component 或通过 @Bean 注册。", e);
        }
    }

    /**
     * 启动期预判消息模板是否含占位符，运行期据此短路 {@code String.replace}。
     */
    private static boolean hasPlaceholder(@Nullable String messageTemplate) {
        return StringUtils.hasText(messageTemplate) && (messageTemplate.contains("{exception}")
                || messageTemplate.contains("{exceptionClass}"));
    }

    /**
     * 协议数组 → 不可变 {@link EnumSet}；空数组返回空集合（表示全协议通用）。
     */
    private static Set<OutputProtocol> toProtocolSet(OutputProtocol[] protocols) {
        if (protocols == null || protocols.length == 0) {
            return Set.of();
        }
        EnumSet<OutputProtocol> set = EnumSet.noneOf(OutputProtocol.class);
        set.addAll(Arrays.asList(protocols));
        return set;
    }
}
