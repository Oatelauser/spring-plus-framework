package io.github.oatelauser.springplus.boot.client.interceptor;

import io.github.oatelauser.springplus.boot.client.core.ApiRequest;
import io.github.oatelauser.springplus.boot.client.core.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 带退避与抖动的自动重试过滤器（API 层 {@link ClientFilter}）
 *
 * <pre>
 * apiClient.addFilter(new RetryFilter());                                        // 默认：3 次，GET/HEAD
 * apiClient.addFilter(new RetryFilter(5, Duration.ofMillis(200),
 *         Duration.ofSeconds(10), 0.3,
 *         Set.of(HttpMethod.GET, HttpMethod.POST)));                             // 自定义
 * </pre>
 *
 * <ul>
 *   <li>触发条件：网络异常（hasException）或 5xx（isServerError）；4xx 属业务问题，不重试</li>
 *   <li>默认只重试幂等方法 GET / HEAD；POST 等需显式加入 retryableMethods</li>
 *   <li>指数退避 initialBackoff * 2^(attempt-1)，封顶 maxBackoff，叠加 ±jitterFactor 抖动
 *       （避免同批失败请求同刻重试形成惊群）</li>
 *   <li>order = HIGHEST_PRECEDENCE：包在过滤链最外层，重试时整条链（含日志过滤器）重跑</li>
 *   <li>线程中断不吞：sleep 被中断时恢复中断标记并直接返回当前响应</li>
 * </ul>
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2026-08-28
 * @since 1.1
 */
@Slf4j
public class RetryFilter implements ClientFilter {

    /**
     * 默认可重试方法：仅幂等的 GET / HEAD
     */
    public static final Set<HttpMethod> DEFAULT_IDEMPOTENT_METHODS = Set.of(HttpMethod.GET, HttpMethod.HEAD);

    private final int maxAttempts;
    private final Duration initialBackoff;
    private final Duration maxBackoff;
    private final double jitterFactor;
    private final Set<HttpMethod> retryableMethods;

    public RetryFilter() {
        this(3, Duration.ofMillis(500), Duration.ofSeconds(5), 0.2, DEFAULT_IDEMPOTENT_METHODS);
    }

    /**
     * @param maxAttempts      总尝试次数（含首次），≥1
     * @param initialBackoff   首次退避时长
     * @param maxBackoff       退避上限（指数增长封顶）
     * @param jitterFactor     抖动系数，0 = 不抖动；0.2 = ±20%
     * @param retryableMethods 允许重试的方法集合
     */
    public RetryFilter(int maxAttempts, Duration initialBackoff, Duration maxBackoff,
            double jitterFactor, Set<HttpMethod> retryableMethods) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be >= 1");
        }
        this.maxAttempts = maxAttempts;
        this.initialBackoff = initialBackoff;
        this.maxBackoff = maxBackoff;
        this.jitterFactor = jitterFactor;
        this.retryableMethods = Set.copyOf(retryableMethods);
    }

    @Override
    public ApiResponse<?> doFilter(ApiRequest request, FilterChain chain) {
        if (maxAttempts <= 1 || !retryableMethods.contains(request.getMethod())) {
            return chain.doFilter(request);
        }
        ApiResponse<?> response = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            response = chain.doFilter(request);
            if (!shouldRetry(response) || attempt == maxAttempts) {
                return response;
            }
            if (!sleep(backoff(attempt))) {
                return response;
            }
            log.warn("Retry attempt {}/{} for {} {}", attempt + 1, maxAttempts,
                    request.getMethod(), request.getUri());
        }
        return response;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    private boolean shouldRetry(ApiResponse<?> response) {
        return response.hasException() || response.isServerError();
    }

    /**
     * 指数退避 + 抖动：base * 2^(attempt-1) 封顶 max，再乘 (1 ± jitterFactor)
     */
    private Duration backoff(int attempt) {
        long exponential = initialBackoff.toMillis() << (attempt - 1);
        long capped = Math.min(exponential, maxBackoff.toMillis());
        if (capped <= 0 || jitterFactor <= 0) {
            return Duration.ofMillis(capped);
        }
        double jitterRange = capped * jitterFactor;
        long jittered = Math.round(capped - jitterRange
                + ThreadLocalRandom.current().nextDouble() * jitterRange * 2);
        return Duration.ofMillis(Math.max(0, jittered));
    }

    /**
     * @return false = 线程被中断，放弃后续重试
     */
    private boolean sleep(Duration delay) {
        try {
            Thread.sleep(delay.toMillis());
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

}
