package io.github.oatelauser.springplus.governor.idempotent;

import org.aopalliance.intercept.MethodInvocation;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.SpelEvaluationException;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 幂等注解 SpEL 求值器（V15 收紧）。
 * <p>
 * 表达式来源是开发者写在注解里的编译期常量（可信输入），但仍做双层收紧：
 * <ul>
 *   <li><b>SimpleEvaluationContext（forReadOnlyDataBinding）</b>：仅允许变量引用与属性只读访问
 *       （{@code #orderId}、{@code #cmd.userId}），{@code T()} 类型引用 / {@code new} 构造 /
 *       Bean 引用 / 反射方法调用一律拒绝——未来表达式来源若扩展到运行期（配置中心/请求参数），
 *       求值面天然不含 RCE 载荷</li>
 *   <li><b>表达式缓存</b>：同一段 spel 只解析一次（注解常量集合有限，缓存稳定命中），
 *       消除每请求 new parser + parse 的重复开销</li>
 * </ul>
 * 参数名依赖 {@code -parameters} 编译（本框架根 POM 已全局开启）。
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2026-09-16
 * @since 1.1.0
 */
final class IdempotentSpelEvaluator {

    private static final SpelExpressionParser PARSER = new SpelExpressionParser();
    private static final Map<String, Expression> CACHE = new ConcurrentHashMap<>();

    private IdempotentSpelEvaluator() {
    }

    /**
     * 求值幂等注解的 SpEL：方法参数按名绑定为变量。
     *
     * @return 求值结果的字符串形态，null 原样返回（策略侧决定兜底语义）
     * @throws SpelEvaluationException 表达式越权（T()/new/方法调用）或属性不存在
     */
    static String evaluate(String spel, MethodInvocation invocation) {
        SimpleEvaluationContext context = SimpleEvaluationContext.forReadOnlyDataBinding().build();
        java.lang.reflect.Parameter[] parameters = invocation.getMethod().getParameters();
        Object[] args = invocation.getArguments();
        for (int i = 0; i < parameters.length; i++) {
            context.setVariable(parameters[i].getName(), i < args.length ? args[i] : null);
        }
        Expression expression = CACHE.computeIfAbsent(spel, PARSER::parseExpression);
        Object value = expression.getValue(context);
        return value == null ? null : value.toString();
    }

    /** 仅供测试观测：当前缓存条目数 */
    static int cachedExpressionCount() {
        return CACHE.size();
    }

}
