package io.github.oatelauser.springplus.boot.client.metrics;

import io.github.oatelauser.springplus.boot.client.core.ApiRequest;
import io.github.oatelauser.springplus.boot.client.core.ApiResponse;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.http.HttpStatusCode;

import java.time.Duration;

/**
 * {@link ApiClientMetrics} 的 Micrometer 实现
 * <p>
 * 本类 import micrometer，只在 classpath 存在 Micrometer 时才会被加载
 * （由 {@link ApiClientMetricsPostProcessor} 注册，不满足条件时自动配置整体跳过）。
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2026-08-28
 * @since 1.1
 */
public class MicrometerApiClientMetrics extends ApiClientMetrics {

    public static final String METRIC_NAME = "api.client.requests";

    private final MeterRegistry registry;
    private final String clientName;

    public MicrometerApiClientMetrics(String clientName, MeterRegistry registry) {
        this.registry = registry;
        this.clientName = clientName;
    }

    @Override
    public void record(ApiRequest request, ApiResponse<?> response) {
        Timer timer = Timer.builder(METRIC_NAME)
                .tag("client", clientName)
                .tag("method", String.valueOf(request.getMethod()))
                .tag("status", status(response))
                .description("Api client request duration")
                .register(registry);
        Duration duration = response.getDuration();
        timer.record(duration != null ? duration : Duration.ZERO);
    }

    /**
     * 网络异常记异常类名（cardinality 天然有限），HTTP 响应记状态码
     */
    private static String status(ApiResponse<?> response) {
        if (response.hasException()) {
            return response.getError().getClass().getSimpleName();
        }
        HttpStatusCode code = response.getStatusCode();
        return code != null ? String.valueOf(code.value()) : "unknown";
    }

}
