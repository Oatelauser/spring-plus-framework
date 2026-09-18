package io.github.oatelauser.springplus.boot.client.interceptor;

import io.github.oatelauser.springplus.boot.client.core.ApiRequest;
import io.github.oatelauser.springplus.boot.client.core.ApiResponse;
import org.springframework.core.Ordered;

/**
 * API 层拦截器
 * <p>
 * 拦截 {@link ApiRequest} 和 {@link ApiResponse}，支持：
 * </p>
 * <ul>
 *   <li><b>前置处理</b> — 在 {@code chain.doFilter()} 之前修改请求</li>
 *   <li><b>中断链路</b> — 不调用 {@code chain.doFilter()}，直接返回 ApiResponse</li>
 *   <li><b>后置处理</b> — 在 {@code chain.doFilter()} 之后修改响应</li>
 *   <li><b>异常处理</b> — try-catch 包裹 {@code chain.doFilter()}</li>
 * </ul>
 *
 * <pre>
 * // 示例：请求校验拦截器（可中断）
 * public class ValidationFilter implements ClientFilter {
 *
 *     &#64;Override
 *     public ApiResponse&lt;?&gt; doFilter(ApiRequest request, InterceptorChain chain) {
 *         if (request.getBody() == null &amp;&amp; requiresBody(request)) {
 *             // 中断！不调用 chain.doFilter()
 *             return ApiResponse.failure(
 *                 new IllegalArgumentException("Request body is required"),
 *                 Duration.ZERO, request.getRequestId());
 *         }
 *         // 继续链路
 *         return chain.doFilter(request);
 *     }
 * }
 *
 * // 示例：缓存拦截器
 * public class CacheFilter implements ClientFilter {
 *
 *     &#64;Override
 *     public ApiResponse&lt;?&gt; doFilter(ApiRequest request, InterceptorChain chain) {
 *         // 前置：检查缓存
 *         String cacheKey = buildCacheKey(request);
 *         ApiResponse&lt;?&gt; cached = cache.get(cacheKey);
 *         if (cached != null) {
 *             return cached; // 命中缓存，中断链路
 *         }
 *
 *         // 执行
 *         ApiResponse&lt;?&gt; response = chain.doFilter(request);
 *
 *         // 后置：写入缓存
 *         if (response.isSuccessful()) {
 *             cache.put(cacheKey, response);
 *         }
 *         return response;
 *     }
 * }
 * </pre>
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2026-02-19
 * @since 1.0
 */
public interface ClientFilter extends Ordered {

    /**
     * 请求过滤方法
     *
     * @param request 当前请求（保证 requestId 已存在）
     * @param chain   拦截器链，调用 {@code chain.doFilter(request)} 继续执行
     * @return 响应对象（可以是 chain 返回的，也可以是自己构造的中断响应）
     */
    ApiResponse<?> doFilter(ApiRequest request, FilterChain chain);

    /**
     * 执行顺序（值越小越先执行，在链的越外层）
     * <p>
     * 外层过滤器先执行 doFilter 的前半部分，后执行后半部分（洋葱模型）。
     * </p>
     */
    @Override
    default int getOrder() {
        return 0;
    }

    /**
     * 是否支持流式响应路径（{@code ApiClient.stream(...)}）
     * <p>
     * <b>默认 {@code true}</b>：绝大多数 filter（auth、tracing、requestId、metrics）
     * 只在请求阶段或响应元信息阶段工作，天然支持流式。
     * </p>
     *
     * <p><b>必须返回 {@code false} 的情况：</b>filter 的实现需要读取
     * {@link ApiResponse#getBody()} —— 例如响应缓存、响应内容校验、
     * 响应内容日志等。这些 filter 在流式路径下会和调用方抢同一条流，
     * 必须显式声明不支持流式，框架会在流式调用时自动跳过它们。</p>
     *
     * <p><b>注意：</b>此方法仅影响 API 层 filter（{@link ClientFilter}），
     * 不影响 HTTP 层拦截器（{@link org.springframework.http.client.ClientHttpRequestInterceptor}）。
     * HTTP 层拦截器的流式安全性通过
     * {@link io.github.oatelauser.springplus.boot.client.interceptor.StreamingSafeHttpInterceptor}
     * marker 接口声明。</p>
     *
     * @return {@code true} 表示流式路径下也参与执行；{@code false} 表示流式路径下被跳过
     */
    default boolean supportsStreaming() {
        return true;
    }

}
