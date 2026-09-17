package io.github.oatelauser.springplus.governor.idempotent;

import io.github.oatelauser.springplus.governor.annotation.Idempotent;
import io.github.oatelauser.springplus.governor.annotation.RepeatSubmit;
import io.github.oatelauser.springplus.web.error.ServiceException;
import io.github.oatelauser.springplus.web.response.BusinessStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 两拦截器的核心行为：首过重拒、业务异常回滚 key、幂等结果缓存回放。
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2026-09-16
 * @since 1.0
 */
class IdempotentInterceptorsTest {

    private final InMemoryIdempotentStore store = new InMemoryIdempotentStore();
    private final KeyStrategyResolver resolver = new KeyStrategyResolver(staticFactory());

    private static StaticListableBeanFactory staticFactory() {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("fingerprintStrategy", new FingerprintKeyStrategy());
        return beanFactory;
    }

    @Test
    void repeatSubmitAllowsFirstAndRejectsSecond() throws Throwable {
        RepeatSubmitInterceptor interceptor = new RepeatSubmitInterceptor(store, resolver);
        Method method = Sample.class.getDeclaredMethod("pay", String.class);
        Object target = new Sample();

        assertEquals("ok", interceptor.invoke(
                StubInvocations.of(target, method, "ok", "order-1")));

        ServiceException rejected = assertThrows(ServiceException.class, () -> interceptor.invoke(
                StubInvocations.of(target, method, "ok", "order-1")));
        assertEquals(BusinessStatus.REPEAT_SUBMIT.getCode(), rejected.getCode());
    }

    @Test
    void repeatSubmitReleasesKeyWhenBusinessFails() throws Throwable {
        RepeatSubmitInterceptor interceptor = new RepeatSubmitInterceptor(store, resolver);
        Method method = Sample.class.getDeclaredMethod("pay", String.class);
        Object target = new Sample();

        assertThrows(IllegalStateException.class, () -> interceptor.invoke(
                StubInvocations.failing(target, method, new IllegalStateException("boom"), "order-2")));

        assertEquals("ok", interceptor.invoke(
                StubInvocations.of(target, method, "ok", "order-2")),
                "业务异常后 key 应回滚，允许重试");
    }

    @Test
    void idempotentCachesResultAndReplaysIt() throws Throwable {
        IdempotentInterceptor interceptor = new IdempotentInterceptor(store, resolver);
        Method method = Sample.class.getDeclaredMethod("create", String.class);
        Object target = new Sample();

        assertEquals("first", interceptor.invoke(
                StubInvocations.of(target, method, "first", "order-3")));
        assertEquals("first", interceptor.invoke(
                StubInvocations.of(target, method, "second", "order-3")),
                "时间窗内重复调用应回放缓存结果而非执行");
    }

    @Test
    void idempotentDifferentArgsDoNotCollide() throws Throwable {
        IdempotentInterceptor interceptor = new IdempotentInterceptor(store, resolver);
        Method method = Sample.class.getDeclaredMethod("create", String.class);
        Object target = new Sample();

        assertEquals("first", interceptor.invoke(
                StubInvocations.of(target, method, "first", "order-4")));
        assertEquals("second", interceptor.invoke(
                StubInvocations.of(target, method, "second", "order-5")),
                "不同参数指纹不应命中同一 key");
    }

    @Test
    void idempotentReleasesKeyWhenBusinessFails() throws Throwable {
        IdempotentInterceptor interceptor = new IdempotentInterceptor(store, resolver);
        Method method = Sample.class.getDeclaredMethod("create", String.class);
        Object target = new Sample();

        assertThrows(IllegalStateException.class, () -> interceptor.invoke(
                StubInvocations.failing(target, method, new IllegalStateException("boom"), "order-6")));

        assertEquals("ok", interceptor.invoke(
                StubInvocations.of(target, method, "ok", "order-6")));
    }

    @org.junit.jupiter.api.Test
    void repeatSubmitFailureKeepsKeyWhenReleaseOnFailureDisabled() throws Throwable {
        // releaseOnFailure=false：业务异常后窗口期内仍拒绝（防故意触发异常绕过防重，V19）
        RepeatSubmitInterceptor interceptor = new RepeatSubmitInterceptor(store, resolver);
        Method keep = KeepSample.class.getDeclaredMethod("pay", String.class);
        Object target = new KeepSample();

        assertThrows(IllegalStateException.class, () -> interceptor.invoke(
                StubInvocations.failing(target, keep, new IllegalStateException("boom"), "k1")));

        ServiceException stillRejected = assertThrows(ServiceException.class, () -> interceptor.invoke(
                StubInvocations.of(target, keep, "ok", "k1")));
        assertEquals(BusinessStatus.REPEAT_SUBMIT.getCode(), stillRejected.getCode());
    }

    static class KeepSample {

        @RepeatSubmit(releaseOnFailure = false)
        public String pay(String orderId) {
            return orderId;
        }
    }

    static class Sample {

        @RepeatSubmit
        public String pay(String orderId) {
            return orderId;
        }

        @Idempotent
        public String create(String key) {
            return key;
        }
    }

}
