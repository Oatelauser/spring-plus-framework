package io.github.oatelauser.springplus.boot.client.adapt;

import io.github.oatelauser.springplus.boot.client.ApiClientSettings;

import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.time.Duration;
import java.util.function.Consumer;

/**
 * 支持逐连接定制的 {@link SimpleClientHttpRequestFactory}。
 * <p>
 * 通过覆写 {@code prepareConnection} 在每个连接上应用定制器
 * （trust-all TLS、代理认证头等）。
 * </p>
 * <p>
 * 注意：本类自身就是生效的 factory——连接/读超时必须配置在<b>本实例</b>上。
 * 早期版本曾用 {@code @Delegate} 持有一个已配置的 delegate，
 * 但 Lombok 委托无法转移字段状态，{@code createRequest} 走的是本实例的
 * 默认超时（0 = 无限），SIMPLE 引擎超时全部静默失效——已废弃该写法。
 * </p>
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2026-03-03
 * @since 1.1
 */
@SuppressWarnings("NullableProblems")
class SimpleClientHttpRequestFactoryAdaptor extends SimpleClientHttpRequestFactory {

    private Consumer<HttpURLConnection> customizer;

    @Override
    protected void prepareConnection(HttpURLConnection connection, String httpMethod) throws IOException {
        super.prepareConnection(connection, httpMethod);
        if (customizer != null) {
            customizer.accept(connection);
        }
    }

    /**
     * 从配置直接构建：超时配置在返回的 factory 自身
     */
    static SimpleClientHttpRequestFactory create(ApiClientSettings settings, Consumer<HttpURLConnection> customizer) {
        SimpleClientHttpRequestFactoryAdaptor factory = new SimpleClientHttpRequestFactoryAdaptor();
        Duration connectTimeout = settings.getConnectTimeout();
        if (connectTimeout != null) {
            factory.setConnectTimeout(connectTimeout);
        }
        Duration readTimeout = settings.getReadTimeout();
        if (readTimeout != null) {
            factory.setReadTimeout(readTimeout);
        }
        factory.customizer = customizer;
        return factory;
    }

}
