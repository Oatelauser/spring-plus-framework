package io.github.oatelauser.springplus.boot.client.core;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Function;

/**
 * 通用 HTTP 响应对象
 *
 * @param <T> 响应体类型
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2026-02-19
 * @since 1.0
 */
@Getter
@RequiredArgsConstructor
@SuppressWarnings("ClassCanBeRecord")
public class ApiResponse<T> {

    /**
     * 请求唯一 ID（与 ApiRequest.requestId 一致）
     */
    private final String requestId;
    private final HttpStatusCode statusCode;
    private final HttpHeaders headers;
    private final T body;
    private final String rawBody;
    private final Duration duration;
    private final Exception error;

    // ============ 判断方法 ============

    public boolean isSuccessful() {return statusCode != null && statusCode.is2xxSuccessful();}

    public boolean isClientError() {return statusCode != null && statusCode.is4xxClientError();}

    public boolean isServerError() {return statusCode != null && statusCode.is5xxServerError();}

    public boolean hasException() {return error != null;}

    public boolean hasBody() {return body != null;}

    public int getStatusCodeValue() {return statusCode != null ? statusCode.value() : -1;}

    public Optional<T> bodyOptional() {
        return Optional.ofNullable(body);
    }

    public T requireBody() {
        if (body == null) {
            String msg = "Response body is null. Status: " + statusCode;
            if (rawBody != null) msg += ", Raw: " + rawBody;
            if (error != null) msg += ", Exception: " + error.getMessage();
            throw new IllegalStateException(msg);
        }
        return body;
    }

    /**
     * 成功时映射 body，失败时返回 fallback
     */
    public <R> R map(Function<T, R> mapper, R fallback) {
        return isSuccessful() && body != null ? mapper.apply(body) : fallback;
    }

    public String getHeader(String name) {
        return headers != null ? headers.getFirst(name) : null;
    }

    // ============ 工厂方法 ============

    public static <T> ApiResponse<T> success(String requestId, HttpStatusCode status,
            HttpHeaders headers, T body, Duration duration) {
        return new ApiResponse<>(requestId, status, headers, body, null, duration, null);
    }

    public static <T> ApiResponse<T> error(String requestId, HttpStatusCode status,
            HttpHeaders headers, String rawBody, Duration duration) {
        return new ApiResponse<>(requestId, status, headers, null, rawBody, duration, null);
    }

    public static <T> ApiResponse<T> failure(Exception ex, Duration duration, String requestId) {
        return new ApiResponse<>(requestId, null, null, null, null, duration, ex);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("ApiResponse[");
        if (requestId != null) sb.append(requestId).append(", ");
        if (hasException()) {
            sb.append("EXCEPTION: ").append(error.getMessage());
        } else {
            sb.append(getStatusCodeValue()).append(", ").append(duration.toMillis()).append("ms");
        }
        sb.append("]");
        return sb.toString();
    }

}
