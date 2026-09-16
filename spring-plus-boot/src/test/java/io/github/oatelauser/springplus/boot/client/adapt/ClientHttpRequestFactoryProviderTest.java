package io.github.oatelauser.springplus.boot.client.adapt;

import io.github.oatelauser.springplus.boot.client.ApiClientSettings;
import io.github.oatelauser.springplus.boot.client.HttpClientEngine;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.lang.reflect.Field;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P2 insecure 守卫 + P1-1 SIMPLE 超时回归：
 * <ul>
 *   <li>verify=false 未显式 allow-insecure=true → 启动失败</li>
 *   <li>SIMPLE + trust-all 路径：超时必须配置在 Adaptor 自身（旧 @Delegate 写法超时静默失效）</li>
 * </ul>
 */
class ClientHttpRequestFactoryProviderTest {

    @Test
    void insecureTlsWithoutExplicitAllowanceFails() {
        ApiClientSettings settings = new ApiClientSettings();
        settings.getSsl().setVerify(false);
        ClientHttpRequestFactoryProvider provider = new ClientHttpRequestFactoryProvider(null);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> provider.createClientHttpRequestFactory(settings));
        assertTrue(error.getMessage().contains("allow-insecure"));
    }

    @Test
    void simpleEngineAdaptorCarriesTimeouts() throws Exception {
        ApiClientSettings settings = new ApiClientSettings();
        settings.setEngine(HttpClientEngine.SIMPLE);
        settings.setConnectTimeout(Duration.ofSeconds(3));
        settings.setReadTimeout(Duration.ofSeconds(4));
        settings.getSsl().setVerify(false);
        settings.getSsl().setAllowInsecure(true);

        ClientHttpRequestFactory factory =
                new ClientHttpRequestFactoryProvider(null).createClientHttpRequestFactory(settings);

        assertTrue(factory instanceof SimpleClientHttpRequestFactory);
        assertEquals(3000, intField(factory, "connectTimeout"));
        assertEquals(4000, intField(factory, "readTimeout"));
    }

    private static int intField(Object target, String name) throws Exception {
        Field field = SimpleClientHttpRequestFactory.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.getInt(target);
    }

}
