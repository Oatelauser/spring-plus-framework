package io.github.oatelauser.springplus.boot.client;

import io.github.oatelauser.springplus.boot.client.metrics.ApiClientMetrics;
import io.github.oatelauser.springplus.boot.client.core.ApiRequest;
import io.github.oatelauser.springplus.boot.client.core.ApiResponse;
import io.github.oatelauser.springplus.boot.client.core.StreamingBody;
import io.github.oatelauser.springplus.boot.client.interceptor.ClientFilter;
import io.github.oatelauser.springplus.boot.client.interceptor.FilterChain;
import io.github.oatelauser.springplus.boot.client.interceptor.LoggingInterceptor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClient.RequestBodySpec;
import org.springframework.web.client.RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/**
 * API 客户端的实现基类
 * <p>
 * 兼容API层和RestClient的调用
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2026-02-19
 * @since 1.0
 */
@Slf4j
@Getter
abstract class BaseApiClient {

    static final String X_REQUEST_ID = "X-Request-Id";

    /**
     * RestClient
     */
    private final RestClient restClient;

    /**
     * 过滤器列表（读多写少，copy-on-write）。
     * <p>
     * 写入时排序；读取时快照即已有序。
     * 不能用按 comparator 去重的有序 Set（如 ConcurrentSkipListSet）：
     * 同 order 的两个过滤器会被判为"重复"，第二个起被静默丢弃。
     * </p>
     */
    private final List<ClientFilter> globalFilters = new CopyOnWriteArrayList<>();

    /**
     * 调用指标（Null Object，默认零开销；容器存在 MeterRegistry 时由自动配置注入）
     */
    private volatile ApiClientMetrics metrics = ApiClientMetrics.DISABLED;

    // ==================== 构造 ====================

    protected BaseApiClient(RestClient restClient, List<ClientFilter> filters) {
        this.restClient = Objects.requireNonNull(restClient, "restClient must not be null");
        this.addGlobalFilters(filters);
    }

    // ==================== 拦截器管理 ====================

    /**
     * 运行时添加拦截器（线程安全）
     * <p>
     * 写操作互斥，保证列表始终有序；
     * 排序通过 copy-on-write 完成，不影响正在执行的请求。
     * </p>
     */
    public void addFilter(ClientFilter interceptor) {
        Objects.requireNonNull(interceptor, "interceptor must not be null");
        this.addGlobalFilters(List.of(interceptor));
    }

    /**
     * 移除全局过滤器
     */
    public void removeFilter(Class<? extends ClientFilter> filterClass) {
        this.globalFilters.removeIf(filterClass::isInstance);
    }

    public void setMetrics(ApiClientMetrics metrics) {
        this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
    }

    private synchronized void addGlobalFilters(Collection<? extends ClientFilter> filters) {
        this.globalFilters.addAll(filters);
        this.globalFilters.sort(AnnotationAwareOrderComparator.INSTANCE);
    }

    // ==================== 内部实现 ====================

    /**
     * 统一执行入口：准备请求 → 走拦截器链 → 执行 HTTP
     */
    @SuppressWarnings("unchecked")
    protected <T> ApiResponse<T> doExecute(ApiRequest request, Function<ApiRequest, ApiResponse<?>> terminal) {
        // 1. 准备请求（注入 requestId）
        ApiRequest prepared = prepareRequest(request);
        Instant start = Instant.now();
        ApiResponse<T> response;

        try {
            // 2. 构建拦截器链，terminal 是真正的 HTTP 调用
            List<ClientFilter> current = this.processRequestFilters(request);
            FilterChain chain = new DefaultFilterChain(current, 0, terminal);

            // 3. 启动链路
            response = (ApiResponse<T>) chain.doFilter(prepared);
        } catch (Exception e) {
            log.error("Request failed: {}", prepared, e);
            response = ApiResponse.failure(e, Duration.between(start, Instant.now()), prepared.getRequestId());
        }
        this.metrics.record(prepared, response);
        return response;
    }

    private List<ClientFilter> processRequestFilters(ApiRequest request) {
        List<ClientFilter> requestFilters = request.getFilters();
        if (CollectionUtils.isEmpty(requestFilters)) {
            return List.copyOf(globalFilters);
        }
        requestFilters = new ArrayList<>(requestFilters);
        requestFilters.addAll(globalFilters);
        requestFilters.sort(AnnotationAwareOrderComparator.INSTANCE);
        return requestFilters;
    }

    /**
     * 处理流式请求的过滤器列表：在普通过滤器列表基础上，
     * 过滤掉 {@link ClientFilter#supportsStreaming()} 返回 false 的项。
     */
    private List<ClientFilter> processRequestFiltersForStream(ApiRequest request) {
        return processRequestFilters(request).stream()
                .filter(ClientFilter::supportsStreaming)
                .toList();
    }

    /**
     * 准备请求：确保 requestId 存在，注入 X-Request-Id 请求头
     */
    protected ApiRequest prepareRequest(ApiRequest request) {
        String requestId = request.getRequestId();
        if (!StringUtils.hasText(requestId)) {
            requestId = UUID.randomUUID().toString();
        }
        // 仅在用户未手动设置 X-Request-Id 时自动注入
        ApiRequest.Builder builder = request.toBuilder().requestId(requestId);
        if (!request.getHeaders().containsKey(X_REQUEST_ID)) {
            builder.header(X_REQUEST_ID, requestId);
        }

        return builder.build();
    }

    /**
     * 流式请求统一执行入口：准备请求 → 走流式安全的过滤器链 → 执行流式 HTTP
     * <p>
     * 与 {@link #doExecute} 的区别：
     * <ul>
     *   <li>过滤掉 {@link ClientFilter#supportsStreaming()} 返回 false 的 filter</li>
     *   <li>terminal 操作走 {@link #httpExecuteStream}（不读 body，把流交给调用方）</li>
     * </ul>
     */
    @SuppressWarnings("unchecked")
    protected ApiResponse<StreamingBody> doExecuteStream(ApiRequest request) {
        ApiRequest prepared = prepareRequest(request);
        Instant start = Instant.now();
        ApiResponse<StreamingBody> response;

        try {
            List<ClientFilter> current = this.processRequestFiltersForStream(request);
            FilterChain chain = new DefaultFilterChain(current, 0, this::httpExecuteStream);
            response = (ApiResponse<StreamingBody>) chain.doFilter(prepared);
        } catch (Exception e) {
            log.error("Streaming request failed: {}", prepared, e);
            response = ApiResponse.failure(e, Duration.between(start, Instant.now()), prepared.getRequestId());
        }
        this.metrics.record(prepared, response);
        return response;
    }

    /**
     * 流式 HTTP 执行（拦截器链的终端操作）
     * <p>
     * 关键点：使用 {@code spec.exchange(fn, false)}（Spring Framework 6.1+），
     * lambda 返回后 Spring <b>不</b>自动关闭 response，
     * 由 {@link StreamingBody#close()} 负责关流并归还连接。
     * </p>
     * <p>
     * 所有状态码（2xx / 4xx / 5xx）一律返回 {@code ApiResponse.success}（字段语义复用），
     * 调用方靠 {@link ApiResponse#isSuccessful()} 判断状态码、自己读 body 处理错误响应。
     * 框架不会"擅自"读取错误 body，避免在错误路径上预读流导致连接死锁。
     * </p>
     */
    protected ApiResponse<StreamingBody> httpExecuteStream(ApiRequest request) {
        Instant start = Instant.now();
        String requestId = request.getRequestId();

        try {
            RequestBodySpec spec = restClient.method(request.getMethod()).uri(buildUri(request));
            this.applySetHeaders(spec, request);
            this.applySetBody(spec, request);
            // 注入流式提示标记，让 LoggingInterceptor 识别并跳过 BODY 缓冲
            spec.headers(headers -> headers.set(LoggingInterceptor.STREAMING_HINT, "true"));

            return spec.exchange((req, res) -> {
                Duration duration = Duration.between(start, Instant.now());
                StreamingBody body = new StreamingBody(res, requestId);
                return ApiResponse.success(requestId, res.getStatusCode(),
                        res.getHeaders(), body, duration);
            }, /* close= */ false);
        } catch (Exception e) {
            return ApiResponse.failure(e, Duration.between(start, Instant.now()), requestId);
        }
    }

    /**
     * 真正的 HTTP 执行（拦截器链的终端操作）
     */
    protected <T> ApiResponse<T> httpExecute(ApiRequest request,
            Class<T> clazz, ParameterizedTypeReference<T> typeRef) {
        Instant start = Instant.now();
        String requestId = request.getRequestId();

        try {
            RequestBodySpec spec = restClient.method(request.getMethod()).uri(buildUri(request));
            this.applySetHeaders(spec, request);
            this.applySetBody(spec, request);

            return spec.exchange((req, res) -> {
                Duration duration = Duration.between(start, Instant.now());
                HttpStatusCode status = res.getStatusCode();
                HttpHeaders headers = res.getHeaders();

                if (!status.is2xxSuccessful()) {
                    String rawBody = readRawBody(res);
                    return ApiResponse.error(requestId, status, headers, rawBody, duration);
                }
                T body = readBody(res, clazz, typeRef);
                return ApiResponse.success(requestId, status, headers, body, duration);
            });
        } catch (Exception e) {
            return ApiResponse.failure(e, Duration.between(start, Instant.now()), requestId);
        }
    }

    private URI buildUri(ApiRequest request) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(request.getUri());
        if (request.hasQueryParams()) {
            request.getQueryParams().forEach(builder::queryParam);
        }
        return request.hasUriVars()
                ? builder.buildAndExpand(request.getUriVariables()).toUri()
                : builder.build().toUri();
    }

    private void applySetHeaders(RequestBodySpec spec, ApiRequest request) {
        spec.headers(headers -> {
            if (request.getAccept() != null) {
                headers.setAccept(List.of(request.getAccept()));
            }
            request.getHeaders().forEach(headers::set);
        });
    }

    private void applySetBody(RequestBodySpec spec, ApiRequest request) {
        if (!request.hasBody()) return;
        if (request.getContentType() != null) {
            spec.contentType(request.getContentType());
        }
        spec.body(request.getBody());
    }

    private <T> T readBody(ConvertibleClientHttpResponse res, Class<T> clazz, ParameterizedTypeReference<T> typeRef) {
        try {
            if (clazz == Void.class || clazz == void.class) return null;
            return clazz != null ? res.bodyTo(clazz) : res.bodyTo(typeRef);
        } catch (Exception e) {
            log.warn("Failed to deserialize response body", e);
            return null;
        }
    }

    private String readRawBody(ConvertibleClientHttpResponse res) {
        try {
            return res.bodyTo(String.class);
        } catch (Exception e) {
            return null;
        }
    }

}
