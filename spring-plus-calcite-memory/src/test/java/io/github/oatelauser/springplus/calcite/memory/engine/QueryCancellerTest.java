package io.github.oatelauser.springplus.calcite.memory.engine;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link QueryCanceller} 单元测试：验证超时触发取消、提前清除可阻止取消、非正超时返回 NOOP。
 */
class QueryCancellerTest {

    @Test
    void cancelFiresAfterTimeout() throws Exception {
        QueryCanceller canceller = new QueryCanceller();
        AtomicBoolean canceled = new AtomicBoolean(false);
        Statement stmt = stubStatement(canceled);

        QueryCanceller.Handle handle = canceller.scheduleCancel(stmt, 50);
        Thread.sleep(300);

        assertTrue(canceled.get(), "看门狗应在超时后调用 Statement.cancel()");
        handle.close();
        canceller.shutdown();
    }

    @Test
    void clearBeforeFirePreventsCancel() throws Exception {
        QueryCanceller canceller = new QueryCanceller();
        AtomicBoolean canceled = new AtomicBoolean(false);
        Statement stmt = stubStatement(canceled);

        QueryCanceller.Handle handle = canceller.scheduleCancel(stmt, 100);
        handle.close();
        Thread.sleep(400);

        assertFalse(canceled.get(), "提前清除看门狗后不应再触发取消");
        canceller.shutdown();
    }

    @Test
    void noopWhenTimeoutNonPositive() {
        QueryCanceller canceller = new QueryCanceller();
        Statement stmt = stubStatement(new AtomicBoolean());

        assertSame(QueryCanceller.Handle.NOOP, canceller.scheduleCancel(stmt, 0));
        assertSame(QueryCanceller.Handle.NOOP, canceller.scheduleCancel(stmt, -1));

        canceller.shutdown();
    }

    /**
     * 用动态代理构造只关心 {@code cancel()} 的 {@link Statement} 桩：
     * cancel() 被调用时翻转标志；其余方法返回类型默认值（测试中不会触发）。
     */
    private static Statement stubStatement(AtomicBoolean canceled) {
        return (Statement) Proxy.newProxyInstance(
            Statement.class.getClassLoader(),
            new Class<?>[]{Statement.class},
            (proxy, method, args) -> {
                if ("cancel".equals(method.getName())) {
                    canceled.set(true);
                    return null;
                }
                Class<?> rt = method.getReturnType();
                if (rt == boolean.class) {
                    return false;
                }
                if (rt == int.class) {
                    return 0;
                }
                if (rt == long.class) {
                    return 0L;
                }
                if (rt == short.class) {
                    return (short) 0;
                }
                if (rt == byte.class) {
                    return (byte) 0;
                }
                if (rt == double.class) {
                    return 0.0;
                }
                if (rt == float.class) {
                    return 0.0f;
                }
                return null;
            });
    }
}
