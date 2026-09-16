package io.github.oatelauser.springplus.governor.annotation;

import io.github.oatelauser.springplus.governor.idempotent.FingerprintKeyStrategy;
import io.github.oatelauser.springplus.governor.idempotent.IdempotentKeyStrategy;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * 防重复提交：窗口内相同请求<b>直接拒绝</b>（{@code B0209 请勿重复提交}）。
 * <p>
 * 防狂点 / 脚本重放。窗口内首次请求执行成功后 key 保留至 TTL 自然过期——
 * 窗口内重复提交一律拒绝；执行抛异常则释放 key，允许立即重试。
 * <p>
 * <b>Key 构造：</b>{@code @RepeatSubmit(strategy = XxxStrategy.class, spel = "...")}
 * <ul>
 *   <li>{@code strategy}：实现 {@link IdempotentKeyStrategy} 的 Spring Bean 类，
 *       默认 {@link FingerprintKeyStrategy} 兜底。
 *   <li>{@code spel}：从方法参数中动态提取用于生成 key 的字段。
 * </ul>
 * 自定义策略：实现 {@link IdempotentKeyStrategy} + 加 {@code @Component}，
 * 注解 {@code strategy = MyStrategy.class} 即可生效。
 * <p>
 * 类级标注对所有方法生效，方法级覆盖类级。
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2026-08-28
 * @see io.github.oatelauser.springplus.governor.idempotent.RepeatSubmitInterceptor
 * @see io.github.oatelauser.springplus.governor.idempotent.IdempotentKeyStrategy
 * @since 1.1
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface RepeatSubmit {

    /**
     * 防重窗口长度，默认 5 秒
     */
    long window() default 5;

    TimeUnit unit() default TimeUnit.SECONDS;

    /**
     * Key 策略实现类（Spring Bean），默认按"主体 + 方法签名 + 参数指纹"兜底。
     * <p>
     * 业务自定义：实现 {@link IdempotentKeyStrategy} + 加 {@code @Component} 即可被注解引用。
     */
    Class<? extends IdempotentKeyStrategy> strategy() default FingerprintKeyStrategy.class;

    /**
     * SpEL 表达式，从方法参数中动态提取用于生成 key 的字段。
     * <p>
     * 对 {@link io.github.oatelauser.springplus.governor.idempotent.TokenKeyStrategy} 必填；对 {@link FingerprintKeyStrategy} 可选。
     */
    @SuppressWarnings("SpellCheckingInspection")
    String spel() default "";

}
