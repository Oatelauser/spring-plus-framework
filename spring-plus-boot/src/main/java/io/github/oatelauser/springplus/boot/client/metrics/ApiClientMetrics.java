package io.github.oatelauser.springplus.boot.client.metrics;

import io.github.oatelauser.springplus.boot.client.core.ApiRequest;
import io.github.oatelauser.springplus.boot.client.core.ApiResponse;

/**
 * ApiClient 调用指标打点（Null Object 基类）
 * <p>
 * 默认 {@link #DISABLED} 空实现，零开销；容器存在 MeterRegistry 时由自动配置
 * 为每个 ApiClient Bean 注入 Micrometer 实现（指标名 {@code api.client.requests}，
 * tag：client / method / status）。刻意不含 host tag——uri 常为相对路径，host 拆分不可靠。
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2026-08-28
 * @since 1.1
 */
public class ApiClientMetrics {

    public static final ApiClientMetrics DISABLED = new ApiClientMetrics();

    protected ApiClientMetrics() {
    }

    /**
     * 记录一次 API 调用（含失败：网络异常 / 4xx / 5xx）
     */
    public void record(ApiRequest request, ApiResponse<?> response) {
    }

}
