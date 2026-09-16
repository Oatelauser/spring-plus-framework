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
 * 结果幂等：首次执行后<b>缓存响应</b>，窗口内重复请求<b>返回首次结果</b>。
 * <p>
 * 防网络重试重复扣款类问题（客户端超时重发 → 拿到首次执行结果而不是再扣一次）。
 * <ul>
 *   <li>首次执行中（PROCESSING 占位期内）的并发重复 → 拒绝（B0209）</li>
 *   <li>首次执行异常 → 释放 key，允许重试</li>
 *   <li>流式 / 大响应（超过 {@link #maxCacheBytes()}）不可缓存：直通执行，仅并发防重窗口生效</li>
 * </ul>
 * 类级标注对所有方法生效，方法级覆盖类级。
 * <p>
 * <b>Key 构造：</b>{@code @Idempotent(strategy = XxxStrategy.class, spel = "#orderId")}
 * <ul>
 *   <li>{@code strategy}：实现 {@link IdempotentKeyStrategy} 的 Spring Bean 类，决定"用什么原料"
 *       （用户 + IP / token / 业务 ID...），默认 {@link FingerprintKeyStrategy} 兜底</li>
 *   <li>{@code spel}：从方法参数中动态提取字段（如 {@code "#userId + ':' + #orderId"}），
 *       不填则对全部参数做指纹</li>
 * </ul>
 * 自定义策略：实现 {@link IdempotentKeyStrategy} + 加 {@code @Component}，
 * 注解 {@code strategy = MyStrategy.class} 即可生效。
 * <p>
 * ponytail: 缓存结果按方法返回类型反序列化，泛型擦除位（如 {@code SimpleResponse<T>} 的 T）
 * 重建为 LinkedHashMap——JSON 出参等价，业务代码不要对缓存命中结果做 instanceof 细类型判断。
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2026-08-28
 * @see io.github.oatelauser.springplus.governor.idempotent.IdempotentInterceptor
 * @see io.github.oatelauser.springplus.governor.idempotent.IdempotentKeyStrategy
 * @since 1.1
 */
@Documented
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Idempotent {

    /**
     * 幂等窗口长度，默认 60 秒
     */
    long window() default 60;

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
     * 示例：{@code "#orderId"}、{@code "#userId + ':' + #deviceId"}、{@code "#cmd.token()"}。
     * <p>
     * 对 {@link io.github.oatelauser.springplus.governor.idempotent.TokenKeyStrategy} 必填；
     * 对 {@link FingerprintKeyStrategy} 可选（不填时按全部参数生成指纹）。
     */
    String express() default "";

    /**
     * 响应缓存上限（字节，按序列化后长度计），超过则跳过缓存直通执行
     */
    int maxCacheBytes() default 16384;

}
