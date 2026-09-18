package io.github.oatelauser.springplus.governor.idempotent;

import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 键策略与解析器：指纹提取、策略 Bean 解析与缓存。
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2026-09-16
 * @since 1.0
 */
class KeyStrategyTest {

    @AfterEach
    void cleanRequestContext() {
        org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void fingerprintIncludesSubjectMethodAndArgs() throws Exception {
        FingerprintKeyStrategy strategy = new FingerprintKeyStrategy();
        Method method = SampleService.class.getDeclaredMethod("create", String.class);
        MethodInvocation invocation = StubInvocations.of(new SampleService(), method, "ok", "order-42");

        String raw = strategy.extract("", null, invocation);

        assertNotNull(raw);
        assertTrue(raw.startsWith("anonymous|"), "无请求上下文时主体为 anonymous");
        assertTrue(raw.contains("create"), "指纹应含方法签名");
        assertTrue(raw.endsWith("\"order-42\"]"), "指纹应含参数序列化: " + raw);
        assertEquals("fingerprint", strategy.strategyName());
    }

    @Test
    void fingerprintWithSpelUsesSpelValueOnly() throws Exception {
        FingerprintKeyStrategy strategy = new FingerprintKeyStrategy();
        Method method = SampleService.class.getDeclaredMethod("create", String.class);
        MethodInvocation invocation = StubInvocations.of(new SampleService(), method, "ok", "ignored-arg");

        String raw = strategy.extract("#orderId", "order-42", invocation);

        assertTrue(raw.endsWith("|order-42"), "有 spel 时指纹以 spel 值收尾: " + raw);
    }

    @Test
    void sameInputProducesSameFingerprint() throws Exception {
        FingerprintKeyStrategy strategy = new FingerprintKeyStrategy();
        Method method = SampleService.class.getDeclaredMethod("create", String.class);

        String first = strategy.extract("", null,
                StubInvocations.of(new SampleService(), method, "ok", "x"));
        String second = strategy.extract("", null,
                StubInvocations.of(new SampleService(), method, "ok", "x"));

        assertEquals(first, second, "同输入指纹必须稳定（哈希前置条件）");
    }

    @Test
    void resolverResolvesAndCachesStrategyBean() {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        FingerprintKeyStrategy registered = new FingerprintKeyStrategy();
        beanFactory.addBean("fingerprintStrategy", registered);

        KeyStrategyResolver resolver = new KeyStrategyResolver(beanFactory);

        assertSame(registered, resolver.resolve(FingerprintKeyStrategy.class));
        assertSame(registered, resolver.resolve(FingerprintKeyStrategy.class), "第二次走缓存，仍是同一实例");
    }

    @Test
    void resolverWorksWithDefaultBeanFactory() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerSingleton("fingerprintStrategy", new FingerprintKeyStrategy());

        assertNotNull(new KeyStrategyResolver(beanFactory).resolve(FingerprintKeyStrategy.class));
    }

    /** 测试载体的普通服务类 */
    static class SampleService {

        public String create(String orderId) {
            return orderId;
        }
    }

}
