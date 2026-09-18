package io.github.oatelauser.springplus.governor.idempotent;

import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.Test;
import org.springframework.expression.spel.SpelEvaluationException;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * V15：幂等 SpEL 求值器——SimpleEvaluationContext 只读绑定（T()/new/方法调用拒绝）+ 表达式缓存。
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2026-09-16
 * @since 1.1.0
 */
class IdempotentSpelEvaluatorTest {

    record Cmd(String orderId, long userId) {
    }

    static class Sample {

        public void pay(String orderId, Cmd cmd) {
        }
    }

    private static MethodInvocation invocation(Object... args) throws Exception {
        Method method = Sample.class.getDeclaredMethod("pay", String.class, Cmd.class);
        return StubInvocations.of(new Sample(), method, null, args);
    }

    @Test
    void variableReferenceWorks() throws Exception {
        assertEquals("order-42", IdempotentSpelEvaluator.evaluate("#orderId", invocation("order-42", new Cmd("c", 1))));
    }

    @Test
    void readOnlyPropertyAccessWorks() throws Exception {
        assertEquals("c-99", IdempotentSpelEvaluator.evaluate("#cmd.orderId + '-' + #cmd.userId",
                invocation("x", new Cmd("c", 99))));
    }

    @Test
    void typeReferenceRejected() throws Exception {
        assertThrows(SpelEvaluationException.class, () ->
                IdempotentSpelEvaluator.evaluate("T(java.lang.Runtime)", invocation("x", new Cmd("c", 1))));
    }

    @Test
    void constructorRejected() throws Exception {
        assertThrows(SpelEvaluationException.class, () ->
                IdempotentSpelEvaluator.evaluate("new java.io.File('/tmp')", invocation("x", new Cmd("c", 1))));
    }

    @Test
    void methodInvocationRejected() throws Exception {
        // String.toUpperCase() 属实例方法调用，forReadOnlyDataBinding 下不可用
        assertThrows(SpelEvaluationException.class, () ->
                IdempotentSpelEvaluator.evaluate("#orderId.toUpperCase()", invocation("x", new Cmd("c", 1))));
    }

    @Test
    void expressionsAreCached() throws Exception {
        // 用本用例独有的表达式，避免与其他用例的缓存条目产生顺序依赖
        int before = IdempotentSpelEvaluator.cachedExpressionCount();
        String probe = "#orderId + '-cache-probe'";
        IdempotentSpelEvaluator.evaluate(probe, invocation("a", new Cmd("c", 1)));
        IdempotentSpelEvaluator.evaluate(probe, invocation("b", new Cmd("c", 1)));
        assertEquals(before + 1, IdempotentSpelEvaluator.cachedExpressionCount(), "同表达式只解析一次");
    }

    @Test
    void nullEvaluatesToNull() throws Exception {
        assertEquals(null, IdempotentSpelEvaluator.evaluate("#cmd.orderId", invocation("x", new Cmd(null, 1))));
    }

}
