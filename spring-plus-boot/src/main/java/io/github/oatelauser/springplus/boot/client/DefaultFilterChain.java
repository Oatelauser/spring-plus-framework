package io.github.oatelauser.springplus.boot.client;

import io.github.oatelauser.springplus.boot.client.core.ApiRequest;
import io.github.oatelauser.springplus.boot.client.core.ApiResponse;
import io.github.oatelauser.springplus.boot.client.interceptor.ClientFilter;
import io.github.oatelauser.springplus.boot.client.interceptor.FilterChain;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.function.Function;

/**
 * 拦截器链默认实现
 * <p>
 * 递归结构：每次 doFilter 创建指向下一个拦截器的新 Chain 实例。
 * 当所有拦截器执行完毕，调用 terminal 函数执行真正的 HTTP 请求。
 * </p>
 *
 * <pre>
 * index=0  →  ClientFilter_0.doFilter(req, Chain(index=1))
 *                 ↓ doFilter
 * index=1  →  ClientFilter_1.doFilter(req, Chain(index=2))
 *                 ↓ doFilter
 * index=2  →  terminal.apply(req)  ← 真正的 HTTP 调用
 * </pre>
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2026-02-19
 * @since 1.0
 */
@Slf4j
class DefaultFilterChain implements FilterChain {

    private final int index;
    private final List<ClientFilter> filters;
    private final Function<ApiRequest, ApiResponse<?>> terminal;

    DefaultFilterChain(List<ClientFilter> filters,
            int index, Function<ApiRequest, ApiResponse<?>> terminal) {
        this.index = index;
        this.filters = filters;
        this.terminal = terminal;
    }

    @Override
    public ApiResponse<?> doFilter(ApiRequest request) {
        if (index < filters.size()) {
            // 还有拦截器未执行 → 调用当前拦截器，传入指向下一个的 Chain
            ClientFilter current = filters.get(index);
            if (log.isTraceEnabled()) {
                log.trace("执行请求过滤器: {}", current.getClass().getName());
            }
            FilterChain next = new DefaultFilterChain(filters, index + 1, terminal);
            return current.doFilter(request, next);
        }
        // 所有拦截器执行完毕 → 执行真正的 HTTP 请求
        return terminal.apply(request);
    }

}
