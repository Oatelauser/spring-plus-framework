package io.github.oatelauser.springplus.boot.client.adapt;

import io.github.oatelauser.springplus.boot.client.ApiClientSettings;
import io.github.oatelauser.springplus.boot.client.HttpClientEngine;
import io.github.oatelauser.springplus.boot.utils.InsecureTlsHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.ssl.DefaultClientTlsStrategy;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.util.TimeValue;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.boot.http.client.HttpRedirects;
import org.springframework.boot.ssl.SslBundle;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.boot.thread.Threading;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.core.task.VirtualThreadTaskExecutor;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLParameters;
import java.net.*;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

/**
 * ApiClientSettings → ClientHttpRequestFactory
 *
 * <pre>
 *  通用字段 ────→ ClientHttpRequestFactorySettings（原生 4 字段）
 *                       │
 *  引擎独占 ────→ Builder.withXxxCustomizer(...)
 *                       │
 *                 builder.build(settings) ──→ Factory
 *                       │
 *  SIMPLE 后置 ──→ factory.setProxy(...)      (仅 SIMPLE 需要)
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2026-03-03
 * @since 1.0
 */
@Slf4j
@RequiredArgsConstructor
@SuppressWarnings("NullableProblems")
public class ClientHttpRequestFactoryProvider implements EnvironmentAware {

    @Nullable
    private final SslBundles sslBundles;
    private Environment environment;

    public ClientHttpRequestFactory createClientHttpRequestFactory(ApiClientSettings settings) {
        assertInsecureTlsAllowed(settings.getSsl());
        HttpClientEngine engine = resolveEngine(settings.getEngine());
        log.info("HTTP client: engine={}, baseUrl={}", engine, settings.getFullBaseUrl());

        return switch (engine) {
            case HTTP_COMPONENTS -> this.createHttpComponents(settings);
            case JDK -> this.createJdk(settings);
            case SIMPLE -> this.createSimple(settings);
            default -> throw new IllegalArgumentException("Unsupported engine: " + engine);
        };
    }

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    // ========================= Apache HC5 =========================

    private ClientHttpRequestFactory createHttpComponents(ApiClientSettings settings) {
        var hc5 = settings.getApacheHc5();
        var ssl = settings.getSsl();
        var proxy = settings.getProxy();

        var builder = ClientHttpRequestFactoryBuilder.httpComponents();

        // ① 连接池
        builder = builder.withConnectionManagerCustomizer(cm -> {
            cm.setMaxConnTotal(hc5.getMaxConnections());
            cm.setMaxConnPerRoute(hc5.getMaxConnectionsPerRoute());
            cm.setDefaultConnectionConfig(
                    org.apache.hc.client5.http.config.ConnectionConfig.custom()
                            .setTimeToLive(toTimeValue(hc5.getTimeToLive()))
                            .setValidateAfterInactivity(toTimeValue(hc5.getValidateAfterInactivity()))
                            .build());
        });

        // ② HttpClient 级别：驱逐 + 代理 + 跨主机重定向剥凭据
        builder = builder.withHttpClientCustomizer(hc -> {
            if (settings.getFollowRedirect() && settings.getStripCredentialsOnCrossHostRedirect()) {
                hc.addRequestInterceptorLast(CrossHostCredentialStrippingInterceptor.INSTANCE);
            }
            if (hc5.getEvictExpiredConnections()) {
                hc.evictExpiredConnections();
            }
            if (hc5.getEvictIdleConnections() != null) {
                hc.evictIdleConnections(toTimeValue(hc5.getEvictIdleConnections()));
            }
            configureHc5Proxy(hc, proxy);
        });

        // ③ trust-all TLS
        if (isInsecureTls(ssl)) {
            log.warn("TLS verification DISABLED (HC5) — do NOT use in production!");
            builder = builder.withTlsSocketStrategyFactory(bundle ->
                    new DefaultClientTlsStrategy(InsecureTlsHelper.trustAllContext(),
                            InsecureTlsHelper.allowAllVerifier()));
        }

        return builder.build(toNativeSettings(settings));
    }

    private void configureHc5Proxy(HttpClientBuilder hc, ApiClientSettings.Proxy proxy) {
        if (proxy == null || !proxy.isConfigured()) {
            return;
        }

        hc.setProxy(new HttpHost(proxy.getHost(), proxy.getPort()));
        if (proxy.hasCredentials()) {
            var credentialsProvider = new BasicCredentialsProvider();
            credentialsProvider.setCredentials(new AuthScope(proxy.getHost(), proxy.getPort()),
                    new UsernamePasswordCredentials(proxy.getUsername(), proxy.getPassword().toCharArray()));
            hc.setDefaultCredentialsProvider(credentialsProvider);
        }
    }

    // ========================= JDK HttpClient =========================

    private ClientHttpRequestFactory createJdk(ApiClientSettings settings) {
        var jdkCfg = settings.getJdk();
        var ssl = settings.getSsl();
        var proxy = settings.getProxy();
        var builder = ClientHttpRequestFactoryBuilder.jdk();
        builder = builder.withHttpClientCustomizer(hc -> {
            // 协议版本
            hc.version(switch (jdkCfg.getVersion()) {
                case HTTP_1_1 -> HttpClient.Version.HTTP_1_1;
                case HTTP_2 -> HttpClient.Version.HTTP_2;
            });

            // 线程池（非 Spring 环境下 environment 为 null，跳过虚拟线程探测）
            if (this.environment != null && Threading.VIRTUAL.isActive(this.environment)) {
                hc.executor(new VirtualThreadTaskExecutor("REST-CLIENT-"));
            }

            // 代理
            if (proxy != null && proxy.isConfigured()) {
                hc.proxy(ProxySelector.of(new InetSocketAddress(proxy.getHost(), proxy.getPort())));
                if (proxy.hasCredentials()) {
                    hc.authenticator(new Authenticator() {
                        @Override
                        protected PasswordAuthentication getPasswordAuthentication() {
                            return getRequestorType() != RequestorType.PROXY ? null :
                                    new PasswordAuthentication(proxy.getUsername(), proxy.getPassword().toCharArray());
                        }
                    });
                }
            }

            // trust-all TLS
            if (isInsecureTls(ssl)) {
                log.warn("TLS verification DISABLED (JDK) — do NOT use in production!");
                hc.sslContext(InsecureTlsHelper.trustAllContext());
                var params = new SSLParameters();
                params.setEndpointIdentificationAlgorithm(null);
                hc.sslParameters(params);
            }
        });
        return builder.build(toNativeSettings(settings));
    }

    // ========================= SIMPLE (HttpURLConnection) =========================

    /**
     * SIMPLE 引擎特殊性：
     * <ul>
     *   <li>代理：{@code SimpleClientHttpRequestFactory.setProxy()} 必须在 build 后设置</li>
     *   <li>SSL：通过 {@code withHttpConnectionCustomizer} 在每个连接上设置</li>
     *   <li>代理认证：HttpURLConnection 不支持 per-connection Authenticator，
     *       通过 {@code Proxy-Authorization} 头手动注入</li>
     *   <li>无连接池、无协议版本选择、无驱逐策略</li>
     * </ul>
     */
    private ClientHttpRequestFactory createSimple(ApiClientSettings settings) {
        var ssl = settings.getSsl();
        var proxy = settings.getProxy();

        // ① SSL verify=false + 代理认证 → 都通过 Connection 级别 Customizer 处理
        boolean insecure = isInsecureTls(ssl);
        boolean proxyAuth = proxy != null && proxy.isConfigured() && proxy.hasCredentials();

        // ② 无需逐连接定制时走 Boot 原生 build（connectTimeout / readTimeout 由原生 Settings 处理）
        if (!insecure && !proxyAuth) {
            return ClientHttpRequestFactoryBuilder.simple().build(toNativeSettings(settings));
        }

        // ③ 需要逐连接定制时用 Adaptor：超时配置在 Adaptor 自身（不能委托给已配置的
        //    delegate——Lombok @Delegate 转移不了字段状态，超时会静默失效）
        SimpleClientHttpRequestFactory factory = SimpleClientHttpRequestFactoryAdaptor.create(settings, conn -> {
            // trust-all：只对 HTTPS 连接生效
            if (insecure && conn instanceof HttpsURLConnection httpsConn) {
                log.warn("TLS verification DISABLED (SIMPLE) — do NOT use in production!");
                httpsConn.setSSLSocketFactory(InsecureTlsHelper.trustAllSocketFactory());
                httpsConn.setHostnameVerifier(InsecureTlsHelper.allowAllVerifier());
            }

            // 代理认证：HttpURLConnection 没有 per-request Authenticator，
            // 最可靠的方式是手动写 Proxy-Authorization 头
            if (proxyAuth) {
                String raw = proxy.getUsername() + ":" + proxy.getPassword();
                String encoded = Base64.getEncoder().encodeToString(
                        raw.getBytes(StandardCharsets.UTF_8));
                conn.setRequestProperty("Proxy-Authorization", "Basic " + encoded);
            }
        });

        // ④ 代理：必须在 build 后设置到 Factory 上
        //    SimpleClientHttpRequestFactory 在 openConnection 时用 URL.openConnection(proxy)
        if (proxy != null && proxy.isConfigured()) {
            factory.setProxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxy.getHost(), proxy.getPort())));
        }
        return factory;
    }

    private HttpClientSettings toNativeSettings(ApiClientSettings s) {
        HttpRedirects redirects = s.getFollowRedirect() ? HttpRedirects.FOLLOW : HttpRedirects.DONT_FOLLOW;
        SslBundle sslBundle = resolveSslBundle(s.getSsl());
        return new HttpClientSettings(null, redirects, s.getConnectTimeout(), s.getReadTimeout(), sslBundle);
    }

    @Nullable
    private SslBundle resolveSslBundle(ApiClientSettings.Ssl ssl) {
        if (ssl.hasBundleName()) {
            if (sslBundles == null) {
                throw new IllegalStateException("ssl.bundle=" + ssl.getBundle()
                        + " configured but no SslBundles bean found");
            }
            return sslBundles.getBundle(ssl.getBundle());
        }
        // verify=false 由各引擎 Customizer 处理，不走 SslBundle
        return null;
    }

    private boolean isInsecureTls(ApiClientSettings.Ssl ssl) {
        return !ssl.hasBundleName() && !ssl.getVerify();
    }

    /**
     * trust-all 双开关：verify=false 必须伴随 allow-insecure=true，
     * 否则直接失败——误关证书校验的代价远大于多写一行配置
     */
    private void assertInsecureTlsAllowed(ApiClientSettings.Ssl ssl) {
        if (isInsecureTls(ssl) && !ssl.getAllowInsecure()) {
            throw new IllegalStateException("ssl.verify=false 需要"
                    + " spring-plus.client.ssl.allow-insecure=true 显式确认后才能启用"
                    + "（trust-all 仅限开发/测试环境）");
        }
    }

    private HttpClientEngine resolveEngine(HttpClientEngine configured) {
        if (configured != HttpClientEngine.AUTO) {
            return configured;
        }
        return isHttpComponentsPresent() ? HttpClientEngine.HTTP_COMPONENTS
                : HttpClientEngine.JDK;  // JDK 11+ 必然存在，不会 fallback 到 SIMPLE
    }

    private static boolean isHttpComponentsPresent() {
        try {
            Class.forName("org.apache.hc.client5.http.impl.classic.HttpClients");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private static TimeValue toTimeValue(Duration d) {
        return d != null ? TimeValue.of(d) : null;
    }

}
