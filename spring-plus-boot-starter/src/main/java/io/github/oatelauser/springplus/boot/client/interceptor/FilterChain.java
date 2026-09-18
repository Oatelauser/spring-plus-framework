package io.github.oatelauser.springplus.boot.client.interceptor;

import io.github.oatelauser.springplus.boot.client.core.ApiRequest;
import io.github.oatelauser.springplus.boot.client.core.ApiResponse;

/**
 * 过滤器链
 * <p>
 * 工作方式与 Servlet {@code FilterChain} 完全一致：
 * </p>
 *
 * <ul>
 *   <li>调用 {@link #doFilter(ApiRequest)} → 继续执行下一个过滤器或最终请求</li>
 *   <li><b>不调用 doFilter</b> → 链路中断，不会发起 HTTP 请求</li>
 * </ul>
 *
 * <pre>
 * ┌─────────────────────────────────────────────────────────┐
 * │                     FilterChain                         │
 * │                                                         │
 * │  Request ──▶ Filter1 ──▶ Filter2 ──▶ Filter3 ──▶ HTTP   │
 * │                │            │            │              │
 * │                │            │            └─ 调用 doFilter│
 * │                │            └─ 调用 doFilter             │
 * │                └─ 调用 doFilter                          │
 * │                                                         │
 * │  如果任何一个 Filter 不调用 doFilter：                    │
 * │                                                         │
 * │  Request ──▶ Filter1 ──▶ Filter2 ──✖ [中断]             │
 * │                │            │                           │
 * │                │            └─ 直接 return Response     │
 * │                │                                        │
 * │                │ ← Response 原路返回                     │
 * └─────────────────────────────────────────────────────────┘
 * </pre>
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2026-02-19
 * @since 1.0
 */
public interface FilterChain {

    /**
     * 将请求传递给链中的下一个拦截器，或传递给最终的 HTTP 执行器
     *
     * @param request 请求对象（可以是修改后的新对象）
     * @return 响应对象
     */
    ApiResponse<?> doFilter(ApiRequest request);

}
