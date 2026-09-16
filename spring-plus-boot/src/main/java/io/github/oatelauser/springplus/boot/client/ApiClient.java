package io.github.oatelauser.springplus.boot.client;

import io.github.oatelauser.springplus.boot.client.adapt.ClientHttpRequestFactoryProvider;
import io.github.oatelauser.springplus.boot.client.core.ApiRequest;
import io.github.oatelauser.springplus.boot.client.core.ApiResponse;
import io.github.oatelauser.springplus.boot.client.core.StreamingBody;
import io.github.oatelauser.springplus.boot.client.interceptor.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.util.Assert;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 统一 API 客户端
 * <p>
 * 两种创建方式：
 * </p>
 *
 * <b>方式一：通过配置类直接创建（非 Spring 环境）</b>
 * <pre>
 * ApiClientProperties props = new ApiClientProperties();
 * props.setBaseUrl("https://api.example.com");
 * props.getCommon().setConnectTimeout(Duration.ofSeconds(5));
 *
 * ApiClient client = ApiClient.create(props);
 * </pre>
 *
 * <b>方式二：通过 Builder 精细控制</b>
 * <pre>
 * ApiClient client = ApiClient.builder()
 *     .properties(props)
 *     .authProvider(myAuth)
 *     .addInterceptor(new CacheInterceptor())
 *     .addInterceptor(new RateLimitInterceptor())
 *     .build();
 * </pre>
 *
 * <b>方式三：Spring 自动装配（推荐）</b>
 * <pre>
 * &#64;Autowired
 * private ApiClient apiClient;
 * </pre>
 *
 * <b>运行时添加拦截器：</b>
 * <pre>
 * apiClient.addInterceptor(new MyInterceptor());
 * </pre>
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2026-02-19
 * @since 1.0
 */
@Slf4j
public class ApiClient extends BaseApiClient {

    public static final String X_REQUEST_ID = BaseApiClient.X_REQUEST_ID;

    protected ApiClient(RestClient restClient, List<ClientFilter> filters) {
        super(restClient, filters);
    }


    // ==================== 静态工厂 ====================

    /**
     * 从 RestClient 创建（最简）
     */
    public static ApiClient create(RestClient restClient) {
        return builder().restClient(restClient).build();
    }

    /**
     * 从配置类创建（自动检测引擎，构建完整链路）
     */
    public static ApiClient create(ApiClientSettings properties) {
        return builder().properties(properties).build();
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * 从 baseUrl 快速创建（最小化配置）
     */
    public static ApiClient create(String baseUrl) {
        ApiClientSettings props = new ApiClientSettings();
        props.setBaseUrl(baseUrl);
        return create(props);
    }

    // ==================== 核心执行方法 ====================

    /**
     * 执行请求（Class 类型）
     */
    public <T> ApiResponse<T> execute(ApiRequest request, Class<T> responseType) {
        return doExecute(request, req -> httpExecute(req, responseType, null));
    }

    /**
     * 执行请求（泛型类型，如 {@code List<User>}）
     */
    public <T> ApiResponse<T> execute(ApiRequest request, ParameterizedTypeReference<T> typeRef) {
        return doExecute(request, req -> httpExecute(req, null, typeRef));
    }

    /**
     * 执行无响应体请求
     */
    public ApiResponse<Void> execute(ApiRequest request) {
        return execute(request, Void.class);
    }

    // ==================== 流式执行 ====================

    /**
     * 执行流式请求，返回未消费的原始响应流
     * <p>
     * 适用场景：LLM SSE 流式输出、NDJSON、大文件下载、自定义流式协议等
     * 任何 <b>响应体不能一次性读完 / 不应该一次性读完</b> 的请求。
     * </p>
     *
     * <p><b>必须 try-with-resources 使用，否则连接泄漏：</b></p>
     * <pre>{@code
     * ApiResponse<StreamingBody> resp = client.stream(
     *         ApiRequest.post("/v1/chat/completions").json(req).build());
     * try (StreamingBody body = resp.requireBody()) {
     *     if (!resp.isSuccessful()) {
     *         // 错误路径：调用方自己读流处理
     *         throw new MyApiException(resp.getStatusCode(),
     *                 new String(body.rawStream().readAllBytes(), UTF_8));
     *     }
     *     try (BufferedReader reader = new BufferedReader(body.rawReader())) {
     *         String line;
     *         while ((line = reader.readLine()) != null) {
     *             // 调用方自己解析 SSE / NDJSON / 自定义协议
     *             handleLine(line);
     *         }
     *     }
     * }
     * }</pre>
     *
     * <p><b>与 {@link #execute} 的差异：</b></p>
     * <ul>
     *   <li>响应 body 类型固定为 {@link StreamingBody}，不接受 {@code Class<T>} 反序列化</li>
     *   <li>4xx / 5xx 响应仍返回 {@link StreamingBody}（非 null），调用方自己决定如何读错误 body</li>
     *   <li>API 层只走 {@link ClientFilter#supportsStreaming()} 返回 true 的过滤器</li>
     * </ul>
     *
     * <p><b>资源泄漏兜底：</b>{@link StreamingBody} 通过 JDK Cleaner 注册了 GC 触发的关流回调。
     * 调用方忘记 close 时，GC 会强制关流并打印 {@code log.error} 含调用栈，
     * 用于压测 / 灰度阶段暴露泄漏点。这是事故防御机制，<b>不应作为日常依赖</b>。</p>
     *
     * @param request 请求对象
     * @return 包装了 {@link StreamingBody} 的响应；网络异常时 body=null + error 字段被填充
     */
    public ApiResponse<StreamingBody> stream(ApiRequest request) {
        return doExecuteStream(request);
    }

    // ==================== 便捷方法 ====================

    public <T> ApiResponse<T> get(String uri, Class<T> type) {
        return execute(ApiRequest.get(uri).build(), type);
    }

    public <T> ApiResponse<T> get(String uri, Class<T> type, Map<String, Object> uriVars) {
        return execute(ApiRequest.get(uri).uriVars(uriVars).build(), type);
    }

    public <T> ApiResponse<T> post(String uri, Object body, Class<T> type) {
        return execute(ApiRequest.post(uri).json(body).build(), type);
    }

    public <T> ApiResponse<T> put(String uri, Object body, Class<T> type) {
        return execute(ApiRequest.put(uri).json(body).build(), type);
    }

    public ApiResponse<Void> delete(String uri) {
        return execute(ApiRequest.delete(uri).build());
    }

    public ApiResponse<Void> delete(String uri, Map<String, Object> uriVars) {
        return execute(ApiRequest.delete(uri).uriVars(uriVars).build());
    }


    // ==================== Builder ====================

    /**
     * ApiClient 构建器
     * <p>
     * 支持两种模式：
     * <ul>
     *   <li><b>RestClient 模式</b>：直接传入已配置的 RestClient</li>
     *   <li><b>Properties 模式</b>：传入配置，自动构建 RestClient（含引擎检测、HTTP 拦截器）</li>
     * </ul>
     */
    public static class Builder {

        private RestClient restClient;
        private AuthProvider authProvider;
        private ApiClientSettings properties;
        private ClientHttpRequestFactoryProvider factoryProvider;
        private final List<ClientFilter> filters = new ArrayList<>();

        /**
         * 直接提供 RestClient（跳过引擎构建和 HTTP 拦截器配置）
         */
        public Builder restClient(RestClient restClient) {
            this.restClient = restClient;
            return this;
        }

        /**
         * 提供配置类（自动构建 RestClient）
         */
        public Builder properties(ApiClientSettings properties) {
            this.properties = properties;
            return this;
        }

        /**
         * 认证提供者（Properties 模式下自动注册 HTTP 层 AuthInterceptor）
         */
        public Builder authProvider(AuthProvider authProvider) {
            this.authProvider = authProvider;
            return this;
        }

        public Builder clientHttpRequestFactoryProvider(ClientHttpRequestFactoryProvider factoryProvider) {
            this.factoryProvider = factoryProvider;
            return this;
        }

        /**
         * 添加 API 层拦截器（拦截 ApiRequest/ApiResponse）
         */
        public Builder addFilter(ClientFilter interceptor) {
            this.filters.add(Objects.requireNonNull(interceptor));
            return this;
        }

        /**
         * 构建 ApiClient
         */
        public ApiClient build() {
            RestClient client = this.restClient;
            if (client != null) {
                return new ApiClient(client, filters);
            }

            Assert.notNull(properties, "Either restClient or properties must be set");
            Assert.hasText(properties.getBaseUrl(), "properties.baseUrl must not be null");
            if (properties.getSsrf().getEnabled()) {
                // SSRF 防护（默认关闭）：链首校验目标主机（绝对 URI 覆盖 baseUrl 是主注入路径）
                String baseUrlHost = java.net.URI.create(properties.getFullBaseUrl()).getHost();
                this.filters.add(0, new io.github.oatelauser.springplus.boot.client.interceptor.SsrfGuardFilter(
                        properties.getSsrf(), baseUrlHost));
                log.info("ApiClient: SSRF guard enabled (allowedHosts={}, denyPrivateNetwork={})",
                        properties.getSsrf().getAllowedHosts(), properties.getSsrf().getDenyPrivateNetwork());
            }
            if (this.factoryProvider == null) {
                // 未显式提供 provider 时用默认实例（无 SslBundles；
                // ssl.bundle 配置在此模式下不可用，需要时请通过 Spring 装配注入）
                log.info("ApiClient: using default ClientHttpRequestFactoryProvider (no SslBundles)");
                this.factoryProvider = new ClientHttpRequestFactoryProvider(null);
            }
            client = this.buildRestClient();
            return new ApiClient(client, filters);
        }

        // ===== Properties 模式：内部构建 RestClient =====

        private RestClient buildRestClient() {
            RestClient.Builder builder = RestClient.builder()
                    .baseUrl(properties.getBaseUrl())
                    .requestFactory(this.createRequestFactory());
            this.configureDefaultHeaders(builder);
            this.configureHttpInterceptors(builder);
            this.configureErrorHandler(builder);
            return builder.build();
        }

        @SuppressWarnings("all")
        private ClientHttpRequestFactory createRequestFactory() {
            return this.factoryProvider.createClientHttpRequestFactory(this.properties);
        }

        private void configureDefaultHeaders(RestClient.Builder builder) {
            Map<String, String> headers = properties.getDefaultHeaders();
            if (headers != null && !headers.isEmpty()) {
                builder.defaultHeaders(h -> headers.forEach(h::set));
            }
        }

        private void configureHttpInterceptors(RestClient.Builder builder) {
            // 按排序先后设置
            if (properties.getLogging().getEnabled()) {
                builder.requestInterceptor(new LoggingInterceptor(properties.getLogging()));
            }
            if (authProvider != null) {
                builder.requestInterceptor(new AuthInterceptor(authProvider));
            }
            if (properties.getCompression().getEnabled()) {
                builder.requestInterceptor(new GzipCompressInterceptor(properties.getCompression().getMinSize()));
            }
            // 无条件注册：内部流式标记头在任何配置下都不发送到服务端
            builder.requestInterceptor(new StreamingHintInterceptor());
        }

        private void configureErrorHandler(RestClient.Builder builder) {
            builder.defaultStatusHandler(
                    status -> status.is4xxClientError() || status.is5xxServerError(),
                    (req, res) -> { /* 不抛异常，由 httpExecute exchange 统一处理 */ }
            );
        }
    }

}
