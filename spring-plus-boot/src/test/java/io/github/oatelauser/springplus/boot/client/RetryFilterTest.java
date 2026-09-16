package io.github.oatelauser.springplus.boot.client;

import io.github.oatelauser.springplus.boot.client.core.ApiRequest;
import io.github.oatelauser.springplus.boot.client.core.ApiResponse;
import io.github.oatelauser.springplus.boot.client.interceptor.RetryFilter;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link RetryFilter} 重试语义回归：5xx 重试到成功、POST 不重试、4xx 不重试、尝试次数用尽。
 */
class RetryFilterTest {

    private static RetryFilter zeroBackoff() {
        return new RetryFilter(3, Duration.ZERO, Duration.ZERO, 0, RetryFilter.DEFAULT_IDEMPOTENT_METHODS);
    }

    private static ApiResponse<Object> serverError() {
        return ApiResponse.error("t", HttpStatus.INTERNAL_SERVER_ERROR, null, null, Duration.ZERO);
    }

    private static ApiResponse<Object> ok() {
        return ApiResponse.success("t", HttpStatus.OK, null, null, Duration.ZERO);
    }

    @Test
    void retries5xxUntilSuccess() {
        AtomicInteger calls = new AtomicInteger();
        ApiResponse<?> response = zeroBackoff().doFilter(ApiRequest.get("/users").build(),
                req -> calls.incrementAndGet() < 3 ? serverError() : ok());
        assertTrue(response.isSuccessful());
        assertEquals(3, calls.get());
    }

    @Test
    void postIsNotRetriedByDefault() {
        AtomicInteger calls = new AtomicInteger();
        ApiResponse<?> response = zeroBackoff().doFilter(ApiRequest.post("/users").build(),
                req -> {
                    calls.incrementAndGet();
                    return serverError();
                });
        assertTrue(response.isServerError());
        assertEquals(1, calls.get());
    }

    @Test
    void clientErrorIsNotRetried() {
        AtomicInteger calls = new AtomicInteger();
        ApiResponse<?> response = zeroBackoff().doFilter(ApiRequest.get("/users/42").build(),
                req -> {
                    calls.incrementAndGet();
                    return ApiResponse.error("t", HttpStatus.NOT_FOUND, null, null, Duration.ZERO);
                });
        assertTrue(response.isClientError());
        assertEquals(1, calls.get());
    }

    @Test
    void stopsAfterMaxAttempts() {
        AtomicInteger calls = new AtomicInteger();
        ApiResponse<?> response = zeroBackoff().doFilter(ApiRequest.get("/users").build(),
                req -> {
                    calls.incrementAndGet();
                    return serverError();
                });
        assertTrue(response.isServerError());
        assertEquals(3, calls.get());
    }

}
