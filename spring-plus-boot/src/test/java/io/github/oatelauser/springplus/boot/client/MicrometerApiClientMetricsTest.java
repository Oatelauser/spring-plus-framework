package io.github.oatelauser.springplus.boot.client;

import io.github.oatelauser.springplus.boot.client.core.ApiRequest;
import io.github.oatelauser.springplus.boot.client.core.ApiResponse;
import io.github.oatelauser.springplus.boot.client.metrics.MicrometerApiClientMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.net.ConnectException;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Micrometer 打点回归：tag 正确（client / method / status），异常路径记异常类名。
 */
class MicrometerApiClientMetricsTest {

    @Test
    void recordsStatusAndTags() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MicrometerApiClientMetrics metrics = new MicrometerApiClientMetrics("test-client", registry);
        ApiRequest request = ApiRequest.get("/ping").build();

        metrics.record(request,
                ApiResponse.success("t", HttpStatus.OK, null, null, Duration.ofMillis(5)));
        assertEquals(1, registry.get("api.client.requests")
                .tag("client", "test-client").tag("method", "GET").tag("status", "200")
                .timer().count());

        metrics.record(request,
                ApiResponse.failure(new ConnectException("refused"), Duration.ZERO, "t"));
        assertEquals(1, registry.get("api.client.requests")
                .tag("status", "ConnectException")
                .timer().count());
    }

}
