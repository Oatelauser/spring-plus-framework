package io.github.oatelauser.springplus.boot.client.interceptor;

import io.github.oatelauser.springplus.boot.client.ApiClientSettings;
import io.github.oatelauser.springplus.boot.client.ApiClientSettings.Logging.LogLevel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import static io.github.oatelauser.springplus.boot.client.ApiClient.X_REQUEST_ID;

/**
 * 日志拦截器：记录请求和响应信息
 *
 * <p>日志级别说明：</p>
 * <ul>
 *   <li><b>NONE</b>   — 不记录任何日志</li>
 *   <li><b>BASIC</b>  — 请求行 + 响应行（方法、URL、状态码、耗时）</li>
 *   <li><b>HEADERS</b>— BASIC + 请求头 + 响应头</li>
 *   <li><b>BODY</b>   — HEADERS + 请求体 + 响应体（自动缓冲，支持下游重读）</li>
 * </ul>
 *
 * <p>安全特性：</p>
 * <ul>
 *   <li>敏感请求头自动脱敏（Authorization、Cookie 等）</li>
 *   <li>请求体/响应体超长自动截断</li>
 *   <li>仅对文本类型的 Content-Type 记录 body，二进制跳过</li>
 * </ul>
 *
 * <pre>
 * --> POST https://api.example.com/v1/users [req-id-123]
 *   Content-Type: application/json
 *   Authorization: [REDACTED]
 *   Body: {"name":"test","email":"test@example.com"}
 * <-- 201 Created (125ms)
 *   Content-Type: application/json
 *   Body: {"id":1,"name":"test"}
 * </pre>
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2026-02-19
 * @since 1.0
 */
@Slf4j
@SuppressWarnings("NullableProblems")
public class LoggingInterceptor implements ClientHttpRequestInterceptor {

    /**
     * 流式请求提示标记（内部 header，不会发送到服务端）
     * <p>
     * 当 {@link io.github.oatelauser.springplus.boot.client.BaseApiClient} 发起流式请求时，
     * 会注入此 header 到 {@link org.springframework.http.HttpRequest} 中。
     * LoggingInterceptor 在入口处识别并立即移除该 header，
     * 避免对响应 body 做 {@link BufferedClientHttpResponse} 缓冲（会导致流式响应死锁），
     * 同时仍然输出 BASIC / HEADERS 级别的访问日志。
     * </p>
     */
    public static final String STREAMING_HINT = "X-Apartment-Streaming-Hint";

    /**
     * 敏感请求头名称（不区分大小写匹配）
     */
    private static final Set<String> SENSITIVE_HEADER_NAMES = Set.of(
            "authorization", "x-api-key", "cookie", "set-cookie",
            "proxy-authorization", "x-csrf-token"
    );

    /**
     * 可记录 body 的 Content-Type（文本类型）
     */
    private static final Set<String> LOGGABLE_CONTENT_TYPES = Set.of(
            "application/json",
            "application/xml",
            "text/plain",
            "text/html",
            "text/xml",
            "application/x-www-form-urlencoded"
    );

    private final LogLevel level;
    private final int maxBodyLogSize;
    private final boolean logSensitiveHeaders;

    public LoggingInterceptor(ApiClientSettings.Logging properties) {
        this.level = properties.getLevel();
        this.maxBodyLogSize = properties.getMaxBodyLogSize();
        this.logSensitiveHeaders = properties.getSensitiveHeaders();
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body,
            ClientHttpRequestExecution execution) throws IOException {
        if (level == LogLevel.NONE) {
            return execution.execute(request, body);
        }

        // 检测流式请求提示标记
        boolean streaming = Boolean.parseBoolean(request.getHeaders().getFirst(STREAMING_HINT));
        // 立即移除，不发送到服务端
        if (streaming) {
            request.getHeaders().remove(STREAMING_HINT);
        }

        // 尝试从请求头获取 requestId
        String requestId = request.getHeaders().getFirst(X_REQUEST_ID);
        // 记录请求
        this.logRequest(requestId, request, body);

        ClientHttpResponse response;
        long startTime = System.currentTimeMillis();
        try {
            response = execution.execute(request, body);
        } catch (IOException e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("<-- HTTP FAILED: {} {} [{}] → {} ({}ms)", request.getMethod(),
                    request.getURI(), requestId, e.getMessage(), duration);
            throw e;
        }
        long duration = System.currentTimeMillis() - startTime;

        // BODY 级别需要缓冲响应体（否则下游读不到）
        // 流式请求：跳过缓冲，避免把整个响应读光导致流式死锁；
        // 仍然输出 BASIC / HEADERS 级别的访问日志（请求行 + 状态码 + 耗时 + 响应头）
        if (level == LogLevel.BODY && !streaming) {
            // 缓冲有上界：超过 maxBodyLogSize 只缓冲前缀，尾部直通下游
            response = new BufferedClientHttpResponse(response, maxBodyLogSize);
        }

        // 记录响应（流式时仅记录到 HEADERS 级别，不碰 body）
        this.logResponse(request, response, duration, streaming);
        return response;
    }

    // ==================== 请求日志 ====================

    private void logRequest(String requestId, HttpRequest request, byte[] body) {
        // --- 请求行（BASIC 及以上） ---
        StringBuilder requestLine = new StringBuilder();
        requestLine.append("--> ")
                .append(request.getMethod())
                .append(" ")
                .append(request.getURI());

        if (requestId != null) {
            requestLine.append(" [").append(requestId).append("]");
        }
        if (body.length > 0) {
            requestLine.append(" (").append(formatSize(body.length)).append(" body)");
        }
        log.info("{}", requestLine);

        // --- 请求头（HEADERS 及以上） ---
        if (level.ordinal() >= ApiClientSettings.Logging.LogLevel.HEADERS.ordinal()) {
            logHeaders("  ", request.getHeaders());
        }

        // --- 请求体（BODY） ---
        if (level == LogLevel.BODY && body.length > 0) {
            logBody("  Request Body", body, request.getHeaders().getContentType());
        }
    }

    // ==================== 响应日志 ====================

    private void logResponse(HttpRequest request, ClientHttpResponse response,
            long durationMs, boolean streaming) throws IOException {

        HttpStatusCode status = response.getStatusCode();
        String statusText = response.getStatusText();

        // --- 响应行（BASIC 及以上） ---
        StringBuilder responseLine = new StringBuilder();
        responseLine.append("<-- ")
                .append(status.value());

        if (!statusText.isBlank()) {
            responseLine.append(" ").append(statusText);
        }

        responseLine.append(" (").append(durationMs).append("ms)");

        // 流式请求追加标记
        if (streaming) {
            responseLine.append(" [streaming]");
        }

        // 根据状态码选择日志级别
        if (status.is2xxSuccessful()) {
            log.info("{}", responseLine);
        } else if (status.is3xxRedirection()) {
            log.info("{}", responseLine);
        } else if (status.is4xxClientError()) {
            log.warn("{}", responseLine);
        } else {
            log.error("{}", responseLine);
        }

        // --- 响应头（HEADERS 及以上） ---
        if (level.ordinal() >= LogLevel.HEADERS.ordinal()) {
            logHeaders("  ", response.getHeaders());
        }

        // --- 响应体（BODY） ---
        // 流式请求：跳过 body 日志（body 仍在传输中，不能预读）
        if (level == LogLevel.BODY && !streaming
                && response instanceof BufferedClientHttpResponse buffered) {
            byte[] responseBody = buffered.getBufferedBody();
            if (responseBody.length > 0) {
                logBody("  Response Body", responseBody, response.getHeaders().getContentType());
            } else {
                log.debug("  Response Body: (empty)");
            }
        } else if (streaming && level == LogLevel.BODY) {
            log.debug("  Response Body: [streaming, skipped]");
        }
    }

    // ==================== 请求头日志 ====================

    private void logHeaders(String prefix, HttpHeaders headers) {
        headers.forEach((name, values) -> {
            if (isSensitiveHeader(name) && !logSensitiveHeaders) {
                log.debug("{}{}:  [REDACTED]", prefix, name);
            } else {
                values.forEach(value ->
                        log.debug("{}{}: {}", prefix, name, value));
            }
        });
    }

    // ==================== Body 日志 ====================

    private void logBody(String label, byte[] body, MediaType contentType) {
        // 二进制类型不记录内容
        if (!isLoggableContentType(contentType)) {
            log.debug("{}: [binary {} data, {}]", label,
                    contentType, formatSize(body.length));
            return;
        }

        Charset charset = extractCharset(contentType);
        String bodyStr = new String(body, charset);
        // V10/CWE-532：BODY 级日志对敏感键值掩码（password/token/phone 等）
        String masked = io.github.oatelauser.springplus.web.utils.LogSanitizer.maskSensitiveValues(bodyStr);
        String truncated = truncate(masked);

        log.debug("{}: {}", label, truncated);
    }

    // ==================== 工具方法 ====================

    private boolean isSensitiveHeader(String headerName) {
        return SENSITIVE_HEADER_NAMES.contains(headerName.toLowerCase());
    }

    private boolean isLoggableContentType(MediaType contentType) {
        if (contentType == null) {
            return true; // 无 Content-Type 时尝试当文本处理
        }
        String typeSubtype = contentType.getType() + "/" + contentType.getSubtype();
        return LOGGABLE_CONTENT_TYPES.contains(typeSubtype)
                || contentType.getSubtype().contains("json")
                || contentType.getSubtype().contains("xml")
                || contentType.getType().equals("text");
    }

    private Charset extractCharset(MediaType contentType) {
        if (contentType != null && contentType.getCharset() != null) {
            return contentType.getCharset();
        }
        return StandardCharsets.UTF_8;
    }

    private String truncate(String text) {
        if (text == null) return "(null)";
        if (text.length() <= maxBodyLogSize) return text;
        return text.substring(0, maxBodyLogSize)
                + "... [truncated, total " + formatSize(text.length()) + "]";
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + "B";
        if (bytes < 1024 * 1024) return String.format("%.1fKB", bytes / 1024.0);
        return String.format("%.1fMB", bytes / (1024.0 * 1024));
    }

}
