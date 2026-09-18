package io.github.oatelauser.springplus.governor.idempotent;

import org.aopalliance.intercept.MethodInvocation;
import org.jspecify.annotations.Nullable;

/**
 * 幂等 Key 策略 SPI。
 * <p>
 * 策略只负责从请求上下文中抽取"业务唯一标识"——拦截器统一负责加前缀、做 SHA-256、拼接最终 key。
 * <p>
 * 实现类须标注 {@link org.springframework.stereotype.Component} 注册为 Spring Bean。
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2026-08-31
 * @see FingerprintKeyStrategy
 * @see TokenKeyStrategy
 * @since 1.2
 */
@FunctionalInterface
@SuppressWarnings("SpellCheckingInspection")
public interface IdempotentKeyStrategy {

    /**
     * 从方法调用中提取业务唯一标识（raw value），不含前缀和哈希。
     * <p>
     * 拦截器收到返回值后会执行：
     * {@code "idempotent:" + strategyName() + ":" + sha256(rawValue)}
     * <p>
     * 返回 {@code null} 表示无法从当前请求上下文中提取到有效值，
     * 拦截器将抛出 {@link io.github.oatelauser.springplus.web.error.ServiceException}
     * 异常（业务可自行覆盖 {@link extract} 在无法提取时返回兜底值而非抛异常）。
     *
     * @param invocation    当前方法调用（含方法签名和实参列表）
     * @param spelRaw       注解中 spel 表达式的原始值（可能为空串）
     * @param spelValue     spel 表达式的求值结果（{@code spelRaw} 非空时才有意义）
     * @return 业务唯一标识的原始字符串，{@code null} 表示提取失败
     */
    @Nullable
    String extract(String spelRaw, String spelValue, MethodInvocation invocation);

    /**
     * 策略名称，用于生成 key 的前缀部分。
     * <p>
     * 拦截器生成的最终 key 格式为：
     * {@code idempotent:<strategyName()>:<sha256(rawValue)>}
     */
    default String strategyName() {
        return "default";
    }

}
