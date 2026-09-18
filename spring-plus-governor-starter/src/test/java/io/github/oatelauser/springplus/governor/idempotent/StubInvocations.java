package io.github.oatelauser.springplus.governor.idempotent;

import org.aopalliance.intercept.MethodInvocation;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Method;

/**
 * 测试用 {@link MethodInvocation} 最小实现（避免引入 mockito）。
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2026-09-16
 * @since 1.0
 */
final class StubInvocations {

    private StubInvocations() {
    }

    /**
     * @param proceedReturn proceed() 的固定返回值
     */
    static MethodInvocation of(Object target, Method method, Object proceedReturn, Object... args) {
        return new MethodInvocation() {
            @Override
            public Object proceed() {
                return proceedReturn;
            }

            @Override
            public Object getThis() {
                return target;
            }

            @Override
            public AccessibleObject getStaticPart() {
                return method;
            }

            @Override
            public Object[] getArguments() {
                return args;
            }

            @Override
            public Method getMethod() {
                return method;
            }
        };
    }

    /** proceed() 抛出的变体：验证拦截器对业务异常的 key 回滚 */
    static MethodInvocation failing(Object target, Method method, Throwable error, Object... args) {
        return new MethodInvocation() {
            @Override
            public Object proceed() throws Throwable {
                throw error;
            }

            @Override
            public Object getThis() {
                return target;
            }

            @Override
            public AccessibleObject getStaticPart() {
                return method;
            }

            @Override
            public Object[] getArguments() {
                return args;
            }

            @Override
            public Method getMethod() {
                return method;
            }
        };
    }

}
